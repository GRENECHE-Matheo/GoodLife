// Relais IA de Lifoody (Cloudflare Workers).
//
// POST /v1/generate  { task, request }  → réponse Gemini (texte seulement)
//   En-têtes : X-Lifoody-Install (identifiant d'installation aléatoire), X-Lifoody-Integrity (jeton Play Integrity,
//   lié au contenu exact de la requête), X-Lifoody-Purchase (jeton d'achat de l'abonnement, si la personne en a un).
// GET  /v1/status → offre (premium/gratuit) et ce qu'il reste aujourd'hui.
//
// Rien n'est journalisé : ni photo, ni message, ni réponse, ni adresse IP (seulement une empreinte salée du jour,
// gardée 24 h, pour limiter les essais gratuits par réseau).

import { readConfig, type Config, type Env } from "./config";
import { callGemini, mockGemini } from "./gemini";
import { sha256Hex, verifyIntegrity, verifySubscription } from "./google";
import type { Remaining, Tier } from "./limits";
import { BadRequest, cleanRequest, kindOf, TASKS, withServerRules, type Task } from "./validate";

export { GlobalBudget, UserLimiter } from "./durable";

const MAX_BODY = 6_000_000;
const SUB_RECHECK_MS = 6 * 3600 * 1000;      // un abonnement actif est revérifié auprès de Google toutes les 6 h
const SUB_RECHECK_INACTIVE_MS = 10 * 60 * 1000;

const MESSAGES: Record<string, string> = {
  premium_required: "Tes essais IA gratuits sont utilisés. Passe à Lifoody Premium pour continuer.",
  limit_day: "Limite du jour atteinte. Elle repart demain à minuit.",
  limit_minute: "Doucement ! Attends une minute avant la prochaine demande.",
  trial_busy: "Trop d'essais gratuits depuis ce réseau aujourd'hui. Réessaie demain.",
  budget: "Le service IA est très demandé en ce moment. Réessaie un peu plus tard.",
  integrity: "Cette version de l'app n'est pas reconnue. Installe Lifoody depuis Google Play.",
  ai_unavailable: "L'IA ne répond pas pour le moment. Réessaie dans un instant.",
  bad_request: "Demande invalide.",
  config: "Service IA pas encore configuré.",
  not_found: "Introuvable.",
};

function json(status: number, body: unknown, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff", ...extra },
  });
}
const fail = (status: number, code: string, extra: Record<string, string> = {}) => json(status, { error: code, message: MESSAGES[code] ?? code }, extra);

interface Who { tier: Tier; key: string; premiumUntil: number }

/** Qui demande : abonné (jeton d'achat vérifié auprès de Google) ou gratuit (identifiant d'installation). */
async function identify(req: Request, env: Env, cfg: Config, now: number): Promise<Who | Response> {
  const install = req.headers.get("X-Lifoody-Install") ?? "";
  if (!/^[a-f0-9]{32}$/.test(install)) return fail(400, "bad_request");
  const purchase = req.headers.get("X-Lifoody-Purchase") ?? "";
  if (purchase) {
    const key = "p:" + (await sha256Hex("purchase|" + purchase));
    const stub = env.USERS.get(env.USERS.idFromName(key));
    const c = await stub.getSub();
    const fresh = c && (c.active ? now - c.checkedAt < SUB_RECHECK_MS && c.expiryMs > now : now - c.checkedAt < SUB_RECHECK_INACTIVE_MS);
    let active = c?.active ?? false, expiryMs = c?.expiryMs ?? 0;
    if (!fresh) {
      const s = cfg.fakeGoogle
        ? { active: purchase.startsWith("test-premium"), expiryMs: now + 30 * 86400_000, productId: "test" }
        : await verifySubscription(purchase, cfg.packageName, cfg.productIds, env.GOOGLE_SERVICE_ACCOUNT, now);
      active = s.active; expiryMs = s.expiryMs;
      await stub.setSub({ active, expiryMs, checkedAt: now, productId: s.productId }, now);
    }
    if (active) return { tier: "premium", key, premiumUntil: expiryMs };
  }
  return { tier: "free", key: "i:" + (await sha256Hex("install|" + install)), premiumUntil: 0 };
}

async function ipHash(req: Request, env: Env, now: number): Promise<string> {
  const ip = req.headers.get("CF-Connecting-IP") ?? "local";
  const day = new Date(now).toISOString().slice(0, 10);
  return (await sha256Hex(`${env.IP_SALT ?? "lifoody"}|${day}|${ip}`)).slice(0, 16);
}

async function generate(req: Request, env: Env, cfg: Config, now: number): Promise<Response> {
  if (!env.GEMINI_API_KEY && !cfg.mockGemini) return fail(503, "config");
  const len = Number(req.headers.get("Content-Length") ?? "0");
  if (len > MAX_BODY) return fail(413, "bad_request");
  const raw = await req.arrayBuffer();
  if (raw.byteLength > MAX_BODY || raw.byteLength === 0) return fail(413, "bad_request");

  // 1) La vraie app, installée depuis Google Play, sur un vrai appareil (jeton lié au contenu exact de la requête)
  if (cfg.requireIntegrity && !cfg.fakeGoogle) {
    const check = await verifyIntegrity(req.headers.get("X-Lifoody-Integrity") ?? "", cfg.packageName, await sha256Hex(raw), env.GOOGLE_SERVICE_ACCOUNT, cfg.allowBasicIntegrity, now);
    if (!check.ok) return fail(401, "integrity");
  }

  // 2) Une requête de forme connue
  let task: Task, clean;
  try {
    const parsed = JSON.parse(new TextDecoder().decode(raw)) as { task?: string; request?: unknown };
    if (!TASKS.includes(parsed.task as Task)) throw new BadRequest("task");
    task = parsed.task as Task;
    clean = withServerRules(cleanRequest(task, parsed.request));
  } catch (e) {
    return fail(400, "bad_request");
  }
  const kind = kindOf(task, clean);

  // 3) Qui, et dans quelles limites
  const who = await identify(req, env, cfg, now);
  if (who instanceof Response) return who;
  const budget = env.BUDGET.get(env.BUDGET.idFromName("global"));
  if (!(await budget.allowed(cfg.monthlyBudgetMicros, cfg.dailyBudgetMicros, now))) return fail(503, "budget", { "Retry-After": "3600" });
  const user = env.USERS.get(env.USERS.idFromName(who.key));
  const d = await user.consume(who.tier, kind, cfg.limits, now);
  if (!d.ok) {
    const status = d.reason === "premium_required" ? 402 : 429;
    return fail(status, d.reason!, d.reason === "limit_minute" ? { "Retry-After": "60" } : {});
  }
  let ip = "";
  if (who.tier === "free") {
    ip = await ipHash(req, env, now);
    if (!(await budget.trialGate(ip, cfg.limits.trialsPerIpPerDay, cfg.limits.trialsPerDay, now))) {
      await user.refund(who.tier, kind, now);
      return fail(429, "trial_busy");
    }
  }

  // 4) Gemini (modèle choisi par le serveur : le moins cher pour les calculs automatiques)
  const models = kind === "auto" ? cfg.modelsAuto : cfg.models;
  const r = cfg.mockGemini
    ? mockGemini(clean, models[0])
    : await callGemini(clean, models, env.GEMINI_API_KEY!, cfg.thinkingLevel, cfg.mediaResolution);
  if (r.status !== 200) {
    await user.refund(who.tier, kind, now);
    if (ip) await budget.trialRefund(ip, now);
    return fail(r.status === 429 ? 503 : 502, r.status === 429 ? "budget" : "ai_unavailable");
  }
  await budget.add(r.micros, now);
  const left: Remaining = await user.status(who.tier, cfg.limits, now);
  return json(200, r.body, { "X-Lifoody-Remaining": JSON.stringify({ tier: who.tier, ...left }) });
}

async function status(req: Request, env: Env, cfg: Config, now: number): Promise<Response> {
  const who = await identify(req, env, cfg, now);
  if (who instanceof Response) return who;
  const left = await env.USERS.get(env.USERS.idFromName(who.key)).status(who.tier, cfg.limits, now);
  return json(200, {
    tier: who.tier, ...left, premiumUntil: who.premiumUntil,
    limits: { photosPerDay: cfg.limits.premiumPhotosPerDay, messagesPerDay: cfg.limits.premiumMessagesPerDay, freeTrials: cfg.limits.freeTrials },
  });
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    const cfg = readConfig(env);
    const now = Date.now();
    try {
      if (url.pathname === "/health") return json(200, { ok: true });
      if (url.pathname === "/v1/generate" && req.method === "POST") return await generate(req, env, cfg, now);
      if (url.pathname === "/v1/status" && req.method === "GET") return await status(req, env, cfg, now);
      return fail(404, "not_found");
    } catch (e) {
      // Volontairement aucun détail (ni contenu, ni jeton) dans les journaux
      console.error("relay_error", e instanceof Error ? e.message.slice(0, 80) : "unknown");
      return fail(502, "ai_unavailable");
    }
  },
} satisfies ExportedHandler<Env>;
