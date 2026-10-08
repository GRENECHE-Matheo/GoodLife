// Réglages du relais, lus dans les variables d'environnement (wrangler.jsonc) et les secrets (wrangler secret).
// Aucune valeur secrète ici : la clé Gemini et le compte de service Google sont des secrets Cloudflare.

export interface Env {
  USERS: DurableObjectNamespace<import("./durable").UserLimiter>;
  BUDGET: DurableObjectNamespace<import("./durable").GlobalBudget>;

  // Secrets (wrangler secret put …), jamais dans le code
  GEMINI_API_KEY?: string;
  GOOGLE_SERVICE_ACCOUNT?: string;   // JSON du compte de service (Play Developer API + Play Integrity)
  IP_SALT?: string;                  // sel pour pseudonymiser les adresses IP des essais gratuits

  // Variables (non secrètes)
  ENVIRONMENT?: string;              // "production" ou "dev" (essais en local seulement)
  PACKAGE_NAME?: string;             // com.goodlife.app
  PRODUCT_IDS?: string;              // identifiants des abonnements acceptés, séparés par des virgules
  MODELS?: string;                   // modèle principal puis modèles de secours
  THINKING_LEVEL?: string;           // réflexion des modèles Gemini 3 : minimal, low…
  MEDIA_RESOLUTION?: string;         // définition des photos : MEDIA_RESOLUTION_LOW / MEDIUM / HIGH
  REQUIRE_INTEGRITY?: string;        // "true" : seule la vraie app installée depuis Play est acceptée
  ALLOW_BASIC_INTEGRITY?: string;    // "true" : accepte aussi MEETS_BASIC_INTEGRITY (téléphones plus anciens)
  PREMIUM_PHOTOS_PER_DAY?: string;
  PREMIUM_MESSAGES_PER_DAY?: string;
  PREMIUM_AUTO_PER_DAY?: string;
  PER_MINUTE?: string;
  FREE_TRIALS?: string;
  TRIALS_PER_IP_PER_DAY?: string;
  TRIALS_PER_DAY?: string;
  MONTHLY_BUDGET_USD?: string;
  DAILY_BUDGET_USD?: string;

  // Uniquement quand ENVIRONMENT = "dev" : faux Google et faux Gemini pour tester en local
  DEV_FAKE_GOOGLE?: string;
  MOCK_GEMINI?: string;
}

export interface Config {
  dev: boolean;
  packageName: string;
  productIds: string[];
  models: string[];
  thinkingLevel: string;
  mediaResolution: string;
  requireIntegrity: boolean;
  allowBasicIntegrity: boolean;
  limits: Limits;
  monthlyBudgetMicros: number;
  dailyBudgetMicros: number;
  fakeGoogle: boolean;
  mockGemini: boolean;
}

export interface Limits {
  premiumPhotosPerDay: number;
  premiumMessagesPerDay: number;
  premiumAutoPerDay: number;
  perMinute: number;
  freeTrials: number;
  trialsPerIpPerDay: number;
  trialsPerDay: number;
}

const num = (v: string | undefined, d: number, min = 0, max = 1e9) => {
  const n = Number(v);
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : d;
};
const list = (v: string | undefined, d: string[]) => {
  const l = (v ?? "").split(",").map((s) => s.trim()).filter(Boolean);
  return l.length ? l : d;
};

export function readConfig(env: Env): Config {
  const dev = env.ENVIRONMENT === "dev";
  return {
    dev,
    packageName: env.PACKAGE_NAME || "com.goodlife.app",
    productIds: list(env.PRODUCT_IDS, ["lifoody_premium"]),
    models: list(env.MODELS, ["gemini-3.5-flash-lite", "gemini-2.5-flash-lite"]),
    thinkingLevel: env.THINKING_LEVEL || "minimal",
    mediaResolution: env.MEDIA_RESOLUTION || "MEDIA_RESOLUTION_MEDIUM",
    // En production, la vérification Play Integrity est obligatoire (impossible de la couper par erreur hors « dev »)
    requireIntegrity: dev ? env.REQUIRE_INTEGRITY === "true" : true,
    allowBasicIntegrity: env.ALLOW_BASIC_INTEGRITY === "true",
    limits: {
      premiumPhotosPerDay: num(env.PREMIUM_PHOTOS_PER_DAY, 15, 0, 200),
      premiumMessagesPerDay: num(env.PREMIUM_MESSAGES_PER_DAY, 40, 0, 500),
      premiumAutoPerDay: num(env.PREMIUM_AUTO_PER_DAY, 6, 0, 50),
      perMinute: num(env.PER_MINUTE, 8, 1, 60),
      freeTrials: num(env.FREE_TRIALS, 3, 0, 50),
      trialsPerIpPerDay: num(env.TRIALS_PER_IP_PER_DAY, 12, 1, 1000),
      trialsPerDay: num(env.TRIALS_PER_DAY, 600, 1, 1e6),
    },
    monthlyBudgetMicros: Math.round(num(env.MONTHLY_BUDGET_USD, 30, 0, 1e6) * 1e6),
    dailyBudgetMicros: Math.round(num(env.DAILY_BUDGET_USD, 3, 0, 1e5) * 1e6),
    // Les raccourcis de test ne marchent JAMAIS en production
    fakeGoogle: dev && env.DEV_FAKE_GOOGLE === "1",
    mockGemini: dev && env.MOCK_GEMINI === "1",
  };
}
