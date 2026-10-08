// Stockage minimal, dans des Durable Objects (un par personne + un global) : des compteurs, rien d'autre.
// Aucun contenu (photo, message, réponse) n'est jamais enregistré.

import { DurableObject } from "cloudflare:workers";
import type { Limits } from "./config";
import { decide, emptyCounters, parisDay, refund as refundCounters, remaining, type Counters, type Decision, type Remaining, type Tier } from "./limits";
import type { Kind } from "./validate";

/** Statut d'abonnement mémorisé quelques heures (évite d'interroger Google à chaque question). */
export interface SubCache { active: boolean; expiryMs: number; checkedAt: number; productId: string }

/** Données effacées automatiquement après 120 jours sans utilisation (minimisation RGPD). */
const RETENTION_MS = 120 * 24 * 3600 * 1000;

export class UserLimiter extends DurableObject {
  private async counters(now: number): Promise<Counters> {
    return (await this.ctx.storage.get<Counters>("c")) ?? emptyCounters(parisDay(now));
  }
  private async touch(now: number) {
    await this.ctx.storage.setAlarm(now + RETENTION_MS);
  }

  async consume(tier: Tier, kind: Kind, limits: Limits, now: number): Promise<Decision> {
    const d = decide(await this.counters(now), tier, kind, limits, now);
    await this.ctx.storage.put("c", d.counters);   // la fenêtre « minute » avance même en cas de refus
    await this.touch(now);
    return d;
  }

  async refund(tier: Tier, kind: Kind, now: number): Promise<void> {
    await this.ctx.storage.put("c", refundCounters(await this.counters(now), tier, kind));
  }

  async status(tier: Tier, limits: Limits, now: number): Promise<Remaining> {
    return remaining(await this.counters(now), tier, limits, now);
  }

  async getSub(): Promise<SubCache | null> {
    return (await this.ctx.storage.get<SubCache>("sub")) ?? null;
  }
  async setSub(s: SubCache, now: number): Promise<void> {
    await this.ctx.storage.put("sub", s);
    await this.touch(now);
  }

  async alarm(): Promise<void> {
    await this.ctx.storage.deleteAll();
  }
}

interface BudgetState {
  month: string; monthMicros: number;
  day: string; dayMicros: number;
  trialDay: string; trialsToday: number; ipTrials: Record<string, number>;
}

/** Un seul objet pour tout le service : dépenses du mois et du jour, et garde-fou des essais gratuits. */
export class GlobalBudget extends DurableObject {
  private async state(now: number): Promise<BudgetState> {
    const day = parisDay(now), month = day.slice(0, 7);
    const s = (await this.ctx.storage.get<BudgetState>("b")) ??
      { month, monthMicros: 0, day, dayMicros: 0, trialDay: day, trialsToday: 0, ipTrials: {} };
    if (s.month !== month) { s.month = month; s.monthMicros = 0; }
    if (s.day !== day) { s.day = day; s.dayMicros = 0; }
    if (s.trialDay !== day) { s.trialDay = day; s.trialsToday = 0; s.ipTrials = {}; }   // les empreintes d'IP ne durent qu'un jour
    return s;
  }

  /** Le plafond de dépense est-il atteint ? (mois et jour) */
  async allowed(monthlyMicros: number, dailyMicros: number, now: number): Promise<boolean> {
    const s = await this.state(now);
    return s.monthMicros < monthlyMicros && s.dayMicros < dailyMicros;
  }

  async add(micros: number, now: number): Promise<void> {
    const s = await this.state(now);
    s.monthMicros += micros; s.dayMicros += micros;
    await this.ctx.storage.put("b", s);
  }

  /** Essais gratuits : au plus N par réseau (empreinte d'IP salée du jour) et M par jour pour tout le service. */
  async trialGate(ipHash: string, perIp: number, perDay: number, now: number): Promise<boolean> {
    const s = await this.state(now);
    const n = s.ipTrials[ipHash] ?? 0;
    if (n >= perIp || s.trialsToday >= perDay) return false;
    s.ipTrials[ipHash] = n + 1; s.trialsToday++;
    await this.ctx.storage.put("b", s);
    return true;
  }

  async trialRefund(ipHash: string, now: number): Promise<void> {
    const s = await this.state(now);
    if (s.ipTrials[ipHash]) s.ipTrials[ipHash]--;
    s.trialsToday = Math.max(0, s.trialsToday - 1);
    await this.ctx.storage.put("b", s);
  }

  async spent(now: number): Promise<{ monthUsd: number; dayUsd: number }> {
    const s = await this.state(now);
    return { monthUsd: s.monthMicros / 1e6, dayUsd: s.dayMicros / 1e6 };
  }
}
