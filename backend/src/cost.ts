// Coût d'une réponse Gemini, à partir des compteurs de jetons renvoyés par Google (usageMetadata).
// Prix officiels du niveau payant (USD par million de jetons), relevés le 9 octobre 2026 sur
// https://ai.google.dev/gemini-api/docs/pricing — à revérifier de temps en temps.

export interface Price { input: number; output: number; cacheRead: number }

export const PRICES: Record<string, Price> = {
  "gemini-3.5-flash-lite": { input: 0.30, output: 2.50, cacheRead: 0.03 },
  "gemini-2.5-flash-lite": { input: 0.10, output: 0.40, cacheRead: 0.01 },
  "gemini-2.5-flash": { input: 0.30, output: 2.50, cacheRead: 0.03 },
};
/** Modèle inconnu : on compte prudemment au prix le plus élevé de la liste. */
const UNKNOWN: Price = { input: 0.30, output: 2.50, cacheRead: 0.03 };
/** Recherche Google (au-delà des requêtes gratuites du mois) : 14 $ les 1 000 avec Gemini 3.x ; on compte toujours ce prix. */
export const SEARCH_MICROS = 14_000;

export interface Usage {
  promptTokenCount?: number;
  cachedContentTokenCount?: number;
  candidatesTokenCount?: number;
  thoughtsTokenCount?: number;
}

/** Coût en micro-dollars (1 $ = 1 000 000). */
export function costMicros(model: string, u: Usage | undefined, searched = false): number {
  const p = PRICES[model] ?? UNKNOWN;
  const prompt = Math.max(0, u?.promptTokenCount ?? 0);
  const cached = Math.min(prompt, Math.max(0, u?.cachedContentTokenCount ?? 0));
  const out = Math.max(0, (u?.candidatesTokenCount ?? 0) + (u?.thoughtsTokenCount ?? 0));
  const usd = ((prompt - cached) * p.input + cached * p.cacheRead + out * p.output) / 1e6;
  return Math.ceil(usd * 1e6) + (searched ? SEARCH_MICROS : 0);
}
