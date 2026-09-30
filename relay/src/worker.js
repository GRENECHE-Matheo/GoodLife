/**
 * GoodLife relay — Cloudflare Worker
 *
 * - La clé Gemini (secret GEMINI_API_KEY) reste ici, elle n'est jamais envoyée aux téléphones.
 * - Chaque requête doit être signée par l'app (HMAC-SHA256 avec APP_SECRET) et datée (< 5 min).
 * - Quota gratuit : DAILY_LIMIT_PER_INSTALL requêtes/jour/installation + DAILY_LIMIT_PER_IP par IP.
 * - Modèle imposé côté serveur : l'app ne peut pas choisir un modèle plus coûteux.
 */
import { DurableObject } from "cloudflare:workers";

const MAX_BODY = 2_500_000;       // ~1,8 Mo d'image en base64
const MAX_PROMPT = 8_000;
const CLOCK_SKEW_MS = 5 * 60 * 1000;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

const json = (status, obj, extra = {}) =>
  new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...extra },
  });

const enc = new TextEncoder();
const hex = (buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
const fromHex = (s) => {
  if (!/^[0-9a-f]*$/i.test(s) || s.length % 2) return null;
  const out = new Uint8Array(s.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(s.substr(i * 2, 2), 16);
  return out;
};

async function verifySignature(secret, install, time, bodyBytes, sigHex) {
  const sig = fromHex(sigHex || "");
  if (!sig || sig.length !== 32) return false;
  const bodyHash = hex(await crypto.subtle.digest("SHA-256", bodyBytes));
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
  return crypto.subtle.verify("HMAC", key, sig, enc.encode(`${install}\n${time}\n${bodyHash}`));
}

async function callGemini(env, model, prompt, image) {
  const parts = [{ text: prompt }];
  if (image) parts.push({ inline_data: { mime_type: "image/jpeg", data: image } });
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
    method: "POST",
    headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
    body: JSON.stringify({
      contents: [{ role: "user", parts }],
      generationConfig: { responseMimeType: "application/json", temperature: 0.2 },
    }),
  });
  return { status: res.status, body: await res.text() };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      // Indique seulement si le relais est configuré, sans jamais révéler la clé.
      return json(200, {
        ok: true,
        geminiKeyConfigured: Boolean(env.GEMINI_API_KEY),
        appSecretConfigured: Boolean(env.APP_SECRET),
        model: env.MODEL,
        dailyLimit: Number(env.DAILY_LIMIT_PER_INSTALL),
      });
    }

    if (request.method !== "POST" || url.pathname !== "/v1/generate") return json(404, { error: "not_found" });
    if (!env.GEMINI_API_KEY || !env.APP_SECRET) return json(503, { error: "not_configured", message: "Relais pas encore configuré." });

    // ---- Vérification que la requête vient bien de l'app ----
    const install = request.headers.get("x-gl-install") || "";
    const time = request.headers.get("x-gl-time") || "";
    const sig = request.headers.get("x-gl-sig") || "";
    if (!UUID_RE.test(install) || !/^\d{10,15}$/.test(time)) return json(401, { error: "unauthorized" });
    if (Math.abs(Date.now() - Number(time)) > CLOCK_SKEW_MS) {
      return json(401, { error: "clock", message: "L'heure du téléphone semble incorrecte." });
    }
    const len = Number(request.headers.get("content-length") || "0");
    if (len > MAX_BODY) return json(413, { error: "too_large" });
    const bodyBytes = await request.arrayBuffer();
    if (bodyBytes.byteLength > MAX_BODY) return json(413, { error: "too_large" });
    if (!(await verifySignature(env.APP_SECRET, install, time, bodyBytes, sig))) return json(401, { error: "unauthorized" });

    let payload;
    try { payload = JSON.parse(new TextDecoder().decode(bodyBytes)); } catch { return json(400, { error: "bad_json" }); }
    const prompt = typeof payload.prompt === "string" ? payload.prompt : "";
    const image = typeof payload.image === "string" && payload.image.length ? payload.image : null;
    if (!prompt || prompt.length > MAX_PROMPT) return json(400, { error: "bad_prompt" });
    if (image && !/^[A-Za-z0-9+/=]+$/.test(image)) return json(400, { error: "bad_image" });

    // ---- Quota journalier ----
    const ip = request.headers.get("cf-connecting-ip") || "unknown";
    const day = new Date().toISOString().slice(0, 10);
    const limitInstall = Number(env.DAILY_LIMIT_PER_INSTALL) || 10;
    const limitIp = Number(env.DAILY_LIMIT_PER_IP) || 30;
    const limiter = env.LIMITER.get(env.LIMITER.idFromName("global"));
    const keys = [`i:${install}`, `ip:${ip}`];
    const check = await limiter.check(keys, day, [limitInstall, limitIp]);
    if (!check.ok) {
      return json(429, {
        error: "quota",
        limit: limitInstall,
        remaining: 0,
        message: `Tu as utilisé tes ${limitInstall} analyses gratuites aujourd'hui. Ajoute ta propre clé Gemini gratuite dans Paramètres pour continuer.`,
      });
    }

    // ---- Appel à Gemini (modèle unique imposé côté serveur) ----
    const r = await callGemini(env, env.MODEL, prompt, image);
    if (r.status === 429) {
      return json(503, { error: "upstream_quota", message: "Le quota gratuit global est atteint pour le moment. Réessaie plus tard ou ajoute ta propre clé." });
    }
    if (r.status < 200 || r.status >= 300) {
      return json(502, { error: "upstream", message: `Erreur du service IA (${r.status}).` });
    }

    let text = "";
    try {
      const data = JSON.parse(r.body);
      const parts = data?.candidates?.[0]?.content?.parts || [];
      text = parts.filter((p) => !p.thought).map((p) => p.text || "").join("");
    } catch { /* texte vide → erreur ci-dessous */ }
    if (!text) return json(502, { error: "empty", message: "Réponse IA vide." });

    // On ne compte que les requêtes réussies.
    const used = await limiter.increment(keys, day);
    return json(200, { text, limit: limitInstall, remaining: Math.max(0, limitInstall - used) });
  },
};

/** Compteurs journaliers (SQLite intégré au Durable Object, offert dans le plan gratuit). */
export class Limiter extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec("CREATE TABLE IF NOT EXISTS c (k TEXT PRIMARY KEY, day TEXT NOT NULL, n INTEGER NOT NULL)");
  }

  count(k, day) {
    const row = this.sql.exec("SELECT n, day FROM c WHERE k = ?", k).toArray()[0];
    return row && row.day === day ? row.n : 0;
  }

  check(keys, day, limits) {
    for (let i = 0; i < keys.length; i++) {
      if (this.count(keys[i], day) >= limits[i]) return { ok: false };
    }
    return { ok: true };
  }

  increment(keys, day) {
    for (const k of keys) {
      this.sql.exec(
        "INSERT INTO c (k, day, n) VALUES (?, ?, 1) ON CONFLICT(k) DO UPDATE SET n = CASE WHEN c.day = excluded.day THEN c.n + 1 ELSE 1 END, day = excluded.day",
        k, day
      );
    }
    // Ménage : on oublie les compteurs des jours passés.
    this.sql.exec("DELETE FROM c WHERE day < ?", day);
    return this.count(keys[0], day);
  }
}
