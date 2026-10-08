// Règles de quota, sans dépendance à Cloudflare (testées à part).

import type { Limits } from "./config";
import type { Kind } from "./validate";

export type Tier = "premium" | "free";

/** Compteurs d'une personne (identifiant pseudonyme), remis à zéro chaque jour (heure de Paris). */
export interface Counters {
  day: string;          // AAAA-MM-JJ
  photo: number;
  message: number;
  auto: number;
  minute: number;       // minute (epoch) de la fenêtre en cours
  minuteCount: number;
  trialsUsed: number;   // essais gratuits utilisés depuis toujours
}

export const emptyCounters = (day: string): Counters => ({ day, photo: 0, message: 0, auto: 0, minute: 0, minuteCount: 0, trialsUsed: 0 });

export type Refusal = "premium_required" | "limit_day" | "limit_minute";
export interface Decision { ok: boolean; reason?: Refusal; counters: Counters }

/** Jour courant à Paris (les limites quotidiennes repartent à minuit, heure française). */
export function parisDay(now: number): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Paris", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(now));
}

export function dayLimit(tier: Tier, kind: Kind, l: Limits): number {
  if (tier === "free") return Infinity;   // en gratuit, c'est le nombre total d'essais qui compte
  return kind === "photo" ? l.premiumPhotosPerDay : kind === "message" ? l.premiumMessagesPerDay : l.premiumAutoPerDay;
}

/**
 * Décide si une demande passe, et renvoie les compteurs mis à jour (déjà comptée si elle passe).
 * Gratuit : quelques essais au total, pas de calcul automatique en arrière-plan. Premium : limites par jour.
 * Tout le monde : limite par minute (contre les rafales).
 */
export function decide(c0: Counters, tier: Tier, kind: Kind, l: Limits, now: number): Decision {
  const today = parisDay(now);
  const c: Counters = c0.day === today ? { ...c0 } : { ...emptyCounters(today), trialsUsed: c0.trialsUsed };
  const minute = Math.floor(now / 60_000);
  if (c.minute !== minute) { c.minute = minute; c.minuteCount = 0; }
  if (c.minuteCount >= l.perMinute) return { ok: false, reason: "limit_minute", counters: c };
  if (tier === "free") {
    if (kind === "auto" || c.trialsUsed >= l.freeTrials) return { ok: false, reason: "premium_required", counters: c };
    c.trialsUsed++;
  } else {
    if (c[kind] >= dayLimit(tier, kind, l)) return { ok: false, reason: "limit_day", counters: c };
  }
  c[kind]++;
  c.minuteCount++;
  return { ok: true, counters: c };
}

/** Rend la demande si Gemini n'a pas répondu (la personne ne perd rien quand le service échoue). */
export function refund(c: Counters, tier: Tier, kind: Kind): Counters {
  const r = { ...c, [kind]: Math.max(0, c[kind] - 1), minuteCount: Math.max(0, c.minuteCount - 1) };
  if (tier === "free") r.trialsUsed = Math.max(0, c.trialsUsed - 1);
  return r;
}

export interface Remaining { photos: number; messages: number; trials: number }

export function remaining(c0: Counters, tier: Tier, l: Limits, now: number): Remaining {
  const c = c0.day === parisDay(now) ? c0 : { ...emptyCounters(parisDay(now)), trialsUsed: c0.trialsUsed };
  if (tier === "free") return { photos: 0, messages: 0, trials: Math.max(0, l.freeTrials - c.trialsUsed) };
  return {
    photos: Math.max(0, l.premiumPhotosPerDay - c.photo),
    messages: Math.max(0, l.premiumMessagesPerDay - c.message),
    trials: Math.max(0, l.freeTrials - c.trialsUsed),
  };
}
