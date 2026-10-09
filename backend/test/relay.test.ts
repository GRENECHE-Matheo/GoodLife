import { describe, expect, it } from "vitest";
import { costMicros } from "../src/cost";
import { checkVerdict, readSubscription, sha256Hex, signedJwt } from "../src/google";
import { decide, emptyCounters, parisDay, refund, remaining } from "../src/limits";
import { BadRequest, cleanRequest, kindOf, MAX_OUTPUT, withServerRules } from "../src/validate";
import { readConfig, type Env, type Limits } from "../src/config";

const L: Limits = { premiumPhotosPerDay: 15, premiumMessagesPerDay: 40, premiumAutoPerDay: 6, premiumFixesPerDay: 10, perMinute: 8, freeTrials: 3, trialsPerIpPerDay: 12, trialsPerDay: 600 };
const NOON = Date.parse("2026-10-09T10:00:00Z");
const user = (text: string) => ({ role: "user", parts: [{ text }] });

describe("quotas", () => {
  it("gratuit : 3 essais au total, puis Premium demandé", () => {
    let c = emptyCounters(parisDay(NOON));
    for (let i = 0; i < 3; i++) {
      const d = decide(c, "free", "photo", L, NOON + i * 61_000);
      expect(d.ok).toBe(true); c = d.counters;
    }
    const d = decide(c, "free", "message", L, NOON + 10 * 61_000);
    expect(d).toMatchObject({ ok: false, reason: "premium_required" });
    // les essais ne reviennent pas le lendemain
    expect(decide(c, "free", "message", L, NOON + 86_400_000).ok).toBe(false);
  });

  it("gratuit : pas de calcul automatique en arrière-plan", () => {
    expect(decide(emptyCounters(parisDay(NOON)), "free", "auto", L, NOON)).toMatchObject({ ok: false, reason: "premium_required" });
  });

  it("premium : 15 photos par jour, remis à zéro à minuit (Paris)", () => {
    let c = emptyCounters(parisDay(NOON));
    for (let i = 0; i < 15; i++) { const d = decide(c, "premium", "photo", L, NOON + i * 61_000); expect(d.ok).toBe(true); c = d.counters; }
    expect(decide(c, "premium", "photo", L, NOON + 20 * 61_000)).toMatchObject({ ok: false, reason: "limit_day" });
    // les messages restent disponibles
    expect(decide(c, "premium", "message", L, NOON + 20 * 61_000).ok).toBe(true);
    // le lendemain
    expect(decide(c, "premium", "photo", L, NOON + 86_400_000).ok).toBe(true);
  });

  it("limite par minute contre les rafales", () => {
    let c = emptyCounters(parisDay(NOON));
    for (let i = 0; i < 8; i++) { const d = decide(c, "premium", "message", L, NOON + i); expect(d.ok).toBe(true); c = d.counters; }
    expect(decide(c, "premium", "message", L, NOON + 9)).toMatchObject({ ok: false, reason: "limit_minute" });
    expect(decide(c, "premium", "message", L, NOON + 61_000).ok).toBe(true);
  });

  it("une demande échouée est rendue", () => {
    const d = decide(emptyCounters(parisDay(NOON)), "free", "photo", L, NOON);
    const r = refund(d.counters, "free", "photo");
    expect(remaining(r, "free", L, NOON).trials).toBe(3);
  });

  it("minuit à Paris, pas en UTC", () => {
    expect(parisDay(Date.parse("2026-10-09T22:30:00Z"))).toBe("2026-10-10");
  });
});

describe("validation des requêtes", () => {
  it("garde seulement les champs connus et borne la réponse", () => {
    const r = cleanRequest("chat", { contents: [user("Salut")], generationConfig: { temperature: 9, maxOutputTokens: 99999, topK: 3 }, model: "gemini-ultra" });
    expect(r.generationConfig).toEqual({ temperature: 1.2, maxOutputTokens: MAX_OUTPUT.chat });
    expect((r as any).model).toBeUndefined();
  });

  it("coach : seulement les derniers messages, en commençant par une question", () => {
    const contents = Array.from({ length: 31 }, (_, i) => ({ role: i % 2 ? "model" : "user", parts: [{ text: `m${i}` }] }));
    const r = cleanRequest("coach", { contents });
    expect(r.contents.length).toBeLessThanOrEqual(12);
    expect(r.contents[0].role).toBe("user");
    expect(r.contents.at(-1)!.parts[0].text).toBe("m30");
  });

  it("refuse les outils non autorisés, sauf la recherche pour « search »", () => {
    expect(() => cleanRequest("chat", { contents: [user("x")], tools: [{ code_execution: {} }] })).toThrow(BadRequest);
    expect(cleanRequest("search", { contents: [user("x")] }).tools).toEqual([{ google_search: {} }]);
  });

  it("refuse une image qui n'en est pas une", () => {
    expect(() => cleanRequest("photo", { contents: [{ role: "user", parts: [{ inline_data: { mime_type: "text/html", data: "PGgxPg==" } }] }] })).toThrow(BadRequest);
  });

  it("une photo jointe au dernier message compte comme une photo", () => {
    const r = cleanRequest("coach", { contents: [{ role: "user", parts: [{ text: "et ça ?" }, { inline_data: { mime_type: "image/jpeg", data: "AAAA" } }] }] });
    expect(kindOf("coach", r)).toBe("photo");
    expect(kindOf("coach", cleanRequest("coach", { contents: [user("salut")] }))).toBe("message");
  });

  it("le dernier message doit venir de la personne", () => {
    expect(() => cleanRequest("chat", { contents: [user("a"), { role: "model", parts: [{ text: "b" }] }] })).toThrow(BadRequest);
  });

  it("ajoute toujours les règles du serveur en premier", () => {
    const r = withServerRules(cleanRequest("chat", { contents: [user("x")], systemInstruction: { parts: [{ text: "Tu es le chef." }] } }));
    expect(r.systemInstruction!.parts[0].text).toContain("RÈGLES DU SERVEUR LIFOODY");
    expect(r.systemInstruction!.parts[1].text).toBe("Tu es le chef.");
  });
});

describe("coûts", () => {
  it("prix officiels de gemini-3.5-flash-lite", () => {
    // 1 M de jetons envoyés + 1 M reçus = 0,30 + 2,50 $
    expect(costMicros("gemini-3.5-flash-lite", { promptTokenCount: 1_000_000, candidatesTokenCount: 1_000_000 })).toBe(2_800_000);
  });
  it("la réflexion est payée comme la réponse, le cache 10 fois moins cher", () => {
    expect(costMicros("gemini-3.5-flash-lite", { promptTokenCount: 1_000_000, cachedContentTokenCount: 1_000_000, thoughtsTokenCount: 1_000_000 })).toBe(2_530_000);
  });
  it("modèle inconnu : prix le plus prudent", () => {
    expect(costMicros("gemini-99", { promptTokenCount: 1_000_000 })).toBe(300_000);
  });
});

describe("vérifications Google", () => {
  const now = NOON;
  const good = {
    requestDetails: { requestPackageName: "com.goodlife.app", requestHash: "abc", timestampMillis: String(now - 1000) },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: "com.goodlife.app" },
    deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
    accountDetails: { appLicensingVerdict: "LICENSED" },
  };
  it("Play Integrity : la vraie app sur un vrai appareil passe", () => {
    expect(checkVerdict(good, "com.goodlife.app", "abc", now, false).ok).toBe(true);
  });
  it("Play Integrity : app modifiée, requête différente, jeton périmé ou appareil douteux refusés", () => {
    expect(checkVerdict({ ...good, appIntegrity: { appRecognitionVerdict: "UNRECOGNIZED_VERSION", packageName: "com.goodlife.app" } }, "com.goodlife.app", "abc", now, false).why).toBe("app");
    expect(checkVerdict(good, "com.goodlife.app", "autre", now, false).why).toBe("hash");
    expect(checkVerdict(good, "com.goodlife.app", "abc", now + 10 * 60_000, false).why).toBe("expired");
    const basic = { ...good, deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_BASIC_INTEGRITY"] } };
    expect(checkVerdict(basic, "com.goodlife.app", "abc", now, false).why).toBe("device");
    expect(checkVerdict(basic, "com.goodlife.app", "abc", now, true).ok).toBe(true);
    expect(checkVerdict({ ...good, accountDetails: { appLicensingVerdict: "UNLICENSED" } }, "com.goodlife.app", "abc", now, false).why).toBe("license");
  });
  it("abonnement : actif, résilié mais encore payé, expiré, autre produit", () => {
    const sub = (state: string, expiry: string, productId = "lifoody_premium") => ({ subscriptionState: state, lineItems: [{ productId, expiryTime: expiry }] });
    expect(readSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", "2026-11-09T10:00:00Z"), ["lifoody_premium"], now).active).toBe(true);
    expect(readSubscription(sub("SUBSCRIPTION_STATE_CANCELED", "2026-10-20T10:00:00Z"), ["lifoody_premium"], now).active).toBe(true);
    expect(readSubscription(sub("SUBSCRIPTION_STATE_EXPIRED", "2026-10-01T10:00:00Z"), ["lifoody_premium"], now).active).toBe(false);
    expect(readSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", "2026-11-09T10:00:00Z", "autre_app"), ["lifoody_premium"], now).active).toBe(false);
  });
  it("jeton du compte de service signé en RS256 et vérifiable", async () => {
    const kp = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]) as CryptoKeyPair;
    const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", kp.privateKey) as ArrayBuffer);
    const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...pkcs8))}\n-----END PRIVATE KEY-----\n`;
    const jwt = await signedJwt({ client_email: "relay@test.iam.gserviceaccount.com", private_key: pem }, "scope", now);
    const [h, c, s] = jwt.split(".");
    const un = (x: string) => Uint8Array.from(atob(x.replace(/-/g, "+").replace(/_/g, "/")), (ch) => ch.charCodeAt(0));
    expect(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", kp.publicKey, un(s), new TextEncoder().encode(`${h}.${c}`))).toBe(true);
    expect(JSON.parse(new TextDecoder().decode(un(c)))).toMatchObject({ iss: "relay@test.iam.gserviceaccount.com", aud: "https://oauth2.googleapis.com/token" });
  });
  it("empreinte SHA-256", async () => {
    expect(await sha256Hex("abc")).toBe("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  });
});

describe("configuration", () => {
  it("calculs automatiques sur le modèle le moins cher, le reste sur le modèle principal", () => {
    const cfg = readConfig({ ENVIRONMENT: "production" } as unknown as Env);
    expect(cfg.models[0]).toBe("gemini-3.5-flash-lite");
    expect(cfg.modelsAuto[0]).toBe("gemini-2.5-flash-lite");
    expect(cfg.requireIntegrity).toBe(true);
    expect(cfg.mockGemini).toBe(false);
  });
});

describe("corrections de scan", () => {
  it("comptées à part des messages en Premium, comme un essai en gratuit", () => {
    const fix = cleanRequest("fix", { contents: [user("400 g de merguez")] });
    expect(kindOf("fix", fix)).toBe("fix");
    // Une « correction » avec une photo jointe reste une photo (on ne contourne pas la limite des photos)
    const withPhoto = cleanRequest("fix", { contents: [{ role: "user", parts: [{ text: "x" }, { inline_data: { mime_type: "image/jpeg", data: "AAAA" } }] }] });
    expect(kindOf("fix", withPhoto)).toBe("photo");

    let c = emptyCounters(parisDay(NOON));
    for (let i = 0; i < 10; i++) { const d = decide(c, "premium", "fix", L, NOON + i * 61_000); expect(d.ok).toBe(true); c = d.counters; }
    expect(decide(c, "premium", "fix", L, NOON + 11 * 61_000).reason).toBe("limit_day");
    expect(c.message).toBe(0);
    expect(remaining(c, "premium", L, NOON).messages).toBe(40);
    expect(remaining(c, "premium", L, NOON).fixes).toBe(0);

    const free = decide(emptyCounters(parisDay(NOON)), "free", "fix", L, NOON);
    expect(free.ok).toBe(true);
    expect(free.counters.trialsUsed).toBe(1);
  });

  it("anciens compteurs sans « fix » toujours lus", () => {
    const old = { ...emptyCounters(parisDay(NOON)) } as Partial<ReturnType<typeof emptyCounters>>;
    delete old.fix;
    const d = decide(old as ReturnType<typeof emptyCounters>, "premium", "fix", L, NOON);
    expect(d.ok).toBe(true);
    expect(d.counters.fix).toBe(1);
  });

  it("limites par défaut : 8 photos, 20 messages, 10 corrections", () => {
    const cfg = readConfig({ ENVIRONMENT: "production" } as unknown as Env);
    expect([cfg.limits.premiumPhotosPerDay, cfg.limits.premiumMessagesPerDay, cfg.limits.premiumFixesPerDay]).toEqual([8, 20, 10]);
  });
});

