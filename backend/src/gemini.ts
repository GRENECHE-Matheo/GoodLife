// Appel à l'API Gemini avec la clé du serveur. Le modèle est choisi ici (jamais par l'app), avec des modèles de secours.

import { costMicros, type Usage } from "./cost";
import type { CleanRequest } from "./validate";

export interface GeminiResult { status: number; body: any; model: string; micros: number }

const BASE = "https://generativelanguage.googleapis.com/v1beta/models";
const MODEL_NAME = /^[a-z0-9][a-z0-9.\-]{1,60}$/;

/** Réglages ajoutés par le serveur : réflexion au minimum (elle est payée comme la réponse), photos en définition moyenne. */
export function serverConfig(model: string, req: CleanRequest, thinkingLevel: string, mediaResolution: string, withExtras: boolean): Record<string, unknown> {
  const gc: Record<string, unknown> = { ...req.generationConfig };
  if (!withExtras) return gc;
  if (/^gemini-[3-9]/.test(model)) gc.thinkingConfig = { thinkingLevel };
  else if (/^gemini-2\.5/.test(model)) gc.thinkingConfig = { thinkingBudget: model.includes("flash-lite") ? 0 : 512 };
  if (req.images > 0 && mediaResolution) gc.mediaResolution = mediaResolution;
  return gc;
}

function payload(req: CleanRequest, gc: Record<string, unknown>): string {
  const body: Record<string, unknown> = { contents: req.contents, generationConfig: gc };
  if (req.systemInstruction) body.systemInstruction = req.systemInstruction;
  if (req.tools) body.tools = req.tools;
  return JSON.stringify(body);
}

/** Ce que l'app reçoit : le texte (sans les « pensées » du modèle) et, pour la recherche, les sources. Rien d'autre. */
export function slimResponse(j: any): any {
  const c = j?.candidates?.[0];
  const parts = (c?.content?.parts ?? []).filter((p: any) => !p?.thought && typeof p?.text === "string").map((p: any) => ({ text: p.text }));
  const out: any = { candidates: [{ content: { role: "model", parts }, finishReason: c?.finishReason }] };
  if (c?.groundingMetadata) out.candidates[0].groundingMetadata = {
    groundingChunks: c.groundingMetadata.groundingChunks,
    searchEntryPoint: c.groundingMetadata.searchEntryPoint,
    webSearchQueries: c.groundingMetadata.webSearchQueries,
  };
  if (j?.promptFeedback) out.promptFeedback = { blockReason: j.promptFeedback.blockReason };
  return out;
}

export async function callGemini(req: CleanRequest, models: string[], key: string, thinkingLevel: string, mediaResolution: string): Promise<GeminiResult> {
  let last: GeminiResult = { status: 502, body: { error: "unavailable" }, model: "", micros: 0 };
  for (const model of models) {
    if (!MODEL_NAME.test(model)) continue;
    // 1er essai avec les réglages serveur ; si le modèle ne les connaît pas (400), on réessaie sans
    for (const extras of [true, false]) {
      const r = await fetch(`${BASE}/${model}:generateContent`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": key },
        body: payload(req, serverConfig(model, req, thinkingLevel, mediaResolution, extras)),
        redirect: "manual",
      });
      let j: any = null;
      try { j = await r.json(); } catch { j = null; }
      if (r.ok) {
        const searched = Boolean(j?.candidates?.[0]?.groundingMetadata?.webSearchQueries?.length);
        return { status: 200, body: slimResponse(j), model, micros: costMicros(model, j?.usageMetadata as Usage, searched) };
      }
      last = { status: r.status, body: null, model, micros: 0 };
      if (r.status === 400 && extras) {
        const msg = String(j?.error?.message ?? "").toLowerCase();
        if (msg.includes("thinking") || msg.includes("media") || msg.includes("resolution")) continue;
      }
      break;
    }
    // Modèle retiré ou surchargé : on passe au suivant ; autre erreur : on s'arrête
    if (![404, 429, 500, 502, 503, 504].includes(last.status)) break;
  }
  return last;
}

/** Réponse factice pour les essais en local (ENVIRONMENT = dev, MOCK_GEMINI = 1) : aucun appel réel, aucun coût. */
export function mockGemini(req: CleanRequest, model: string): GeminiResult {
  const json = req.generationConfig.responseMimeType === "application/json";
  const text = json
    ? JSON.stringify({ plat: "Test", kcal: 420, reply: "Réponse de test du relais.", meals: [], points: ["Test"] })
    : "Réponse de test du relais.";
  const usage: Usage = { promptTokenCount: 2000, candidatesTokenCount: 300, thoughtsTokenCount: 50 };
  return { status: 200, body: { candidates: [{ content: { role: "model", parts: [{ text }] }, finishReason: "STOP" }] }, model, micros: costMicros(model, usage) };
}
