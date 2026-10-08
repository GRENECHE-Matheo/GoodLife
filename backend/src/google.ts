// Vérifications auprès de Google avec le compte de service (secret GOOGLE_SERVICE_ACCOUNT) :
// - l'abonnement Google Play (Google Play Developer API, purchases.subscriptionsv2) ;
// - l'authenticité de l'app et de l'appareil (Play Integrity API).

const enc = new TextEncoder();

export const b64url = (data: ArrayBuffer | Uint8Array | string): string => {
  const bytes = typeof data === "string" ? enc.encode(data) : data instanceof Uint8Array ? data : new Uint8Array(data);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

export async function sha256Hex(data: ArrayBuffer | Uint8Array | string): Promise<string> {
  const bytes = typeof data === "string" ? enc.encode(data) : data;
  const d = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

interface ServiceAccount { client_email: string; private_key: string; token_uri?: string }

function pemToPkcs8(pem: string): ArrayBuffer {
  const b64 = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const raw = atob(b64);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out.buffer;
}

/** Jeton signé (JWT RS256) du compte de service, échangé contre un jeton d'accès Google. */
export async function signedJwt(sa: ServiceAccount, scope: string, now: number): Promise<string> {
  const iat = Math.floor(now / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({ iss: sa.client_email, scope, aud: sa.token_uri || "https://oauth2.googleapis.com/token", iat, exp: iat + 3600 }));
  const key = await crypto.subtle.importKey("pkcs8", pemToPkcs8(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(`${header}.${claims}`));
  return `${header}.${claims}.${b64url(sig)}`;
}

const SCOPES = "https://www.googleapis.com/auth/androidpublisher https://www.googleapis.com/auth/playintegrity";
let cached: { token: string; until: number } | null = null;   // gardé en mémoire de l'instance, jamais enregistré

export async function accessToken(saJson: string | undefined, now = Date.now()): Promise<string> {
  if (cached && cached.until > now + 60_000) return cached.token;
  if (!saJson) throw new Error("service_account_missing");
  const sa = JSON.parse(saJson) as ServiceAccount;
  const jwt = await signedJwt(sa, SCOPES, now);
  const r = await fetch(sa.token_uri || "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!r.ok) throw new Error(`oauth_${r.status}`);
  const j = (await r.json()) as { access_token: string; expires_in: number };
  cached = { token: j.access_token, until: now + j.expires_in * 1000 };
  return j.access_token;
}

export interface Subscription { active: boolean; expiryMs: number; productId: string }

/** États où l'abonnement donne accès à Premium (une résiliation reste valable jusqu'à la fin de la période payée). */
const ACTIVE = new Set(["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]);

export function readSubscription(j: any, productIds: string[], now: number): Subscription {
  const items: any[] = Array.isArray(j?.lineItems) ? j.lineItems : [];
  const mine = items.filter((it) => productIds.includes(String(it?.productId)));
  const expiryMs = Math.max(0, ...mine.map((it) => Date.parse(it?.expiryTime ?? "") || 0));
  const productId = String(mine[0]?.productId ?? "");
  return { active: ACTIVE.has(String(j?.subscriptionState)) && mine.length > 0 && expiryMs > now, expiryMs, productId };
}

export async function verifySubscription(token: string, packageName: string, productIds: string[], saJson: string | undefined, now = Date.now()): Promise<Subscription> {
  if (!/^[A-Za-z0-9._\-:]{10,2000}$/.test(token)) return { active: false, expiryMs: 0, productId: "" };
  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(token)}`;
  const r = await fetch(url, { headers: { Authorization: `Bearer ${await accessToken(saJson, now)}` } });
  if (r.status === 404 || r.status === 400 || r.status === 410) return { active: false, expiryMs: 0, productId: "" };
  if (!r.ok) throw new Error(`play_${r.status}`);
  return readSubscription(await r.json(), productIds, now);
}

export interface IntegrityCheck { ok: boolean; why?: string }

/** Contrôle du verdict Play Integrity (fonction pure, testée à part). */
export function checkVerdict(p: any, packageName: string, requestHash: string, now: number, allowBasic: boolean): IntegrityCheck {
  const rd = p?.requestDetails, app = p?.appIntegrity, dev = p?.deviceIntegrity, acc = p?.accountDetails;
  if (rd?.requestPackageName !== packageName) return { ok: false, why: "package" };
  if (rd?.requestHash !== requestHash) return { ok: false, why: "hash" };
  const ts = Number(rd?.timestampMillis);
  if (!Number.isFinite(ts) || Math.abs(now - ts) > 5 * 60_000) return { ok: false, why: "expired" };
  if (app?.appRecognitionVerdict !== "PLAY_RECOGNIZED" || app?.packageName !== packageName) return { ok: false, why: "app" };
  const verdicts: string[] = Array.isArray(dev?.deviceRecognitionVerdict) ? dev.deviceRecognitionVerdict : [];
  const deviceOk = verdicts.includes("MEETS_DEVICE_INTEGRITY") || verdicts.includes("MEETS_STRONG_INTEGRITY") || (allowBasic && verdicts.includes("MEETS_BASIC_INTEGRITY"));
  if (!deviceOk) return { ok: false, why: "device" };
  if (acc?.appLicensingVerdict !== "LICENSED") return { ok: false, why: "license" };
  return { ok: true };
}

export async function verifyIntegrity(token: string, packageName: string, requestHash: string, saJson: string | undefined, allowBasic: boolean, now = Date.now()): Promise<IntegrityCheck> {
  if (!token || token.length > 20_000) return { ok: false, why: "missing" };
  const r = await fetch(`https://playintegrity.googleapis.com/v1/${encodeURIComponent(packageName)}:decodeIntegrityToken`, {
    method: "POST",
    headers: { Authorization: `Bearer ${await accessToken(saJson, now)}`, "Content-Type": "application/json" },
    body: JSON.stringify({ integrity_token: token }),
  });
  if (!r.ok) return { ok: false, why: `decode_${r.status}` };
  const j = (await r.json()) as { tokenPayloadExternal?: unknown };
  return checkVerdict(j.tokenPayloadExternal, packageName, requestHash, now, allowBasic);
}
