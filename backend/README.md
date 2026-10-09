# Relais IA de Lifoody (Cloudflare Workers)

Serveur qui fait le lien entre l'app Lifoody (version Google Play) et Google Gemini, pour que les abonnés n'aient
pas à créer leur propre clé.

## Ce qu'il fait

1. Reçoit une demande de l'app (photo de repas, message au coach, planning…).
2. Vérifie que c'est **la vraie app installée depuis Google Play, sur un vrai appareil** (jeton Play Integrity,
   lié au contenu exact de la demande, valable 5 minutes). En production, cette vérification ne peut pas être coupée.
3. Vérifie l'**abonnement** auprès de Google (Google Play Developer API, mémorisé 6 h). Sans abonnement :
   3 essais IA au total.
4. Compte l'usage : **abonnés 15 photos + 40 messages par jour** (+ 6 calculs automatiques : objectifs d'eau et de
   pas), **8 demandes par minute au plus**. Un **plafond de dépense** global (par jour et par mois) coupe le service
   avant que la facture ne puisse s'envoler.
5. Appelle Gemini avec **sa propre clé** (secret Cloudflare), avec le modèle choisi par le serveur et des modèles de
   secours, la réflexion du modèle au minimum (elle est facturée), les photos en définition moyenne.
6. Renvoie seulement le texte de la réponse.

### Ce qui n'est jamais enregistré

Aucune photo, aucun message, aucune réponse, aucun journal de requêtes (`observability` désactivé). Le serveur garde
seulement, pour un identifiant pseudonyme (empreinte du jeton d'achat ou de l'identifiant d'installation) : des
compteurs du jour, le nombre d'essais gratuits utilisés et le statut d'abonnement. Tout est **effacé
automatiquement après 120 jours sans utilisation**. Pour limiter les essais gratuits par réseau, une empreinte salée
de l'adresse IP (mélangée au jour) est effacée **dès le lendemain**.

### La clé Gemini ne peut pas fuiter par l'app

Elle n'est ni dans l'APK, ni sur GitHub, ni dans un fichier : c'est un **secret Cloudflare**, chiffré, illisible
même depuis le tableau de bord une fois enregistré. L'app ne la voit jamais. Restreins-la aussi côté Google
(API Gemini seulement) et utilise les crédits **prépayés sans recharge automatique** : la dépense ne peut alors pas
dépasser ce que tu as mis.

## Essais en local (sans rien payer)

```bash
cd backend
npm install
cp .dev.vars.example .dev.vars   # ENVIRONMENT=dev : faux Google, fausse IA
npm test                          # tests automatiques (quotas, validation, coûts, vérifications Google)
npm run dev                       # serveur sur http://127.0.0.1:8787
bash test/smoke.sh                # essai de bout en bout
```

Les raccourcis de test (`DEV_FAKE_GOOGLE`, `MOCK_GEMINI`) ne fonctionnent **que** si `ENVIRONMENT=dev` ; la
configuration de production (`wrangler.jsonc`) met `ENVIRONMENT=production`.

## Mise en ligne (à faire par toi, le moment venu)

1. **Cloudflare** : choisis ton sous-domaine Workers (dash.cloudflare.com › Workers & Pages, première visite), puis
   `npx wrangler deploy` dans ce dossier. L'adresse du relais ressemble à
   `https://lifoody-relay.<ton-sous-domaine>.workers.dev`.
2. **Secrets** (dans un terminal, dans ce dossier, ou sur le site : le Worker › Paramètres › Variables et secrets ›
   Ajouter › type « Secret ») :
   - `npx wrangler secret put GEMINI_API_KEY` → colle la clé AI Studio (projet lifoody-ia) ;
   - `npx wrangler secret put GOOGLE_SERVICE_ACCOUNT` → colle **tout le contenu** du fichier JSON de la clé du compte
     de service `lifoody-relay`, puis supprime ce fichier de ton ordinateur ;
   - `npx wrangler secret put IP_SALT` → une longue phrase aléatoire (n'importe laquelle, à ne noter nulle part).
3. **Google Cloud (projet lifoody-ia)** : API « Google Play Android Developer » et « Play Integrity » activées ;
   compte de service `lifoody-relay` (sans rôle) avec une clé JSON.
4. **Play Console** : le compte de service invité avec « Afficher les données financières » et « Gérer les commandes
   et les abonnements » ; App integrity › lier le projet Cloud **lifoody-ia** ; abonnement `lifoody_premium` créé
   (offres mensuelle et annuelle, essai gratuit 7 jours).
5. **App** : compiler la version Play avec l'adresse du relais :
   `gradle bundlePlayRelease -PLIFOODY_RELAY_URL=https://lifoody-relay.<sous-domaine>.workers.dev -PLIFOODY_CLOUD_PROJECT=<numéro du projet>`
   (ou variables d'environnement du même nom dans GitHub Actions). Ce ne sont pas des secrets.
6. Vérifier : `curl https://lifoody-relay.<sous-domaine>.workers.dev/health` → `{"ok":true}`.

## Réglages (wrangler.jsonc › vars, modifiables sans mettre à jour l'app)

| Variable | Défaut | Rôle |
|---|---|---|
| `MODELS` | `gemini-3.5-flash-lite,gemini-2.5-flash-lite` | modèle principal puis secours |
| `THINKING_LEVEL` | `minimal` | réflexion des modèles Gemini 3 (payée comme la réponse) |
| `MEDIA_RESOLUTION` | `MEDIA_RESOLUTION_MEDIUM` | définition des photos envoyées au modèle |
| `PREMIUM_PHOTOS_PER_DAY` / `PREMIUM_MESSAGES_PER_DAY` | 15 / 40 | limites des abonnés |
| `FREE_TRIALS` | 3 | essais IA gratuits au total |
| `MONTHLY_BUDGET_USD` / `DAILY_BUDGET_USD` | 30 / 3 | plafond de dépense : au-delà, l'IA se met en pause pour tous |
| `PRODUCT_IDS` | `lifoody_premium` | abonnement(s) acceptés |

## Coût estimé (prix officiels du 9 octobre 2026)

`gemini-3.5-flash-lite` : 0,30 $ par million de jetons envoyés, **2,50 $ par million reçus** (réflexion comprise),
0,03 $ par million pour la partie relue depuis le cache implicite.

| Demande | Jetons (environ) | Coût |
|---|---|---|
| Photo de repas | 2 600 envoyés (photo, consignes, catalogue) + 400 reçus | ≈ 0,18 centime |
| Message au coach | 6 000 envoyés (contexte, 10 derniers messages) + 450 reçus | ≈ 0,2 à 0,3 centime |
| Calcul automatique (eau, pas) | 800 + 80 | ≈ 0,04 centime |

| Profil d'abonné (par jour) | Coût par mois |
|---|---|
| Léger : 1 photo, 2 messages | ≈ 0,23 $ |
| Moyen : 3 photos, 6 messages | ≈ 0,65 $ |
| Maximum autorisé : 15 photos, 40 messages | ≈ 3,90 $ |

Avec `gemini-2.5-flash-lite` (0,10 $ / 0,40 $), ces coûts sont divisés par 4 environ (maximum ≈ 1 $ par mois).

Cache de contexte : le cache **explicite** de Gemini n'est pas utilisé, car il demande un long préambule identique
pour tout le monde (au moins 1 000 à 4 000 jetons selon le modèle) et se paie à l'heure ; ici, les consignes du coach
contiennent le profil de chaque personne. Le **cache implicite** (automatique, 90 % moins cher sur la partie relue)
s'applique dès qu'un même début de requête revient : les règles fixes sont donc placées en premier.
