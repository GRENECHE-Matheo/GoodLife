# Publier GoodLife sur Google Play — aide-mémoire

> Préparé avec l'assistance d'une IA, à partir des règles publiées par Google au 30 septembre 2026.
> Ce n'est pas un avis juridique : relis chaque réponse dans la Play Console, les formulaires évoluent.

## 1. Le fichier à envoyer

- Format **AAB** (obligatoire) : artefact `GoodLife-play-aab` produit par GitHub Actions
  (`gradle bundlePlayRelease`). C'est la version **play** : sans mise à jour intégrée ni permission
  `REQUEST_INSTALL_PACKAGES` (interdites sur Play pour se mettre à jour soi-même).
- Cible **Android 16 (API 36)** : exigé pour toute nouvelle app depuis le 31/08/2026.
- **Signature (Play App Signing)** : à la création de l'app, choisir d'utiliser **ta propre clé**
  (celle de `GOODLIFE_KEYSTORE`) si tu veux que les utilisateurs de l'APK GitHub puissent passer à la
  version Play sans désinstaller. Sinon, Google crée sa propre clé et les deux versions ne pourront pas
  se mettre à jour l'une par l'autre. Ne jamais perdre ni changer la clé.

## 2. Compte développeur

- Compte personnel créé après novembre 2023 : Google impose un **test fermé avec au moins 12 testeurs
  pendant 14 jours** avant la mise en production.
- Vérification d'identité demandée par Google.
- Statut de « professionnel » (DSA, UE) : GoodLife est gratuit, sans pub ni achat → déclarer
  **non-professionnel** si c'est un projet personnel sans but commercial.

## 3. Fiche Play Store

**Catégorie** : Santé et remise en forme.

**À mettre dans la description (obligatoire pour les applis santé)** :

> GoodLife est une application de bien-être. Ce n'est pas un dispositif médical : elle ne permet pas de
> diagnostiquer, traiter, guérir ou prévenir une maladie. Consulte un professionnel de santé pour tout
> avis médical, diagnostic ou traitement.

À ajouter aussi :
- « Réservée aux 15 ans et plus. Fonctions IA réservées aux 18 ans et plus, avec ta propre clé Google Gemini. »
- « Projet développé avec l'assistance d'une IA. »

**E-mail de contact** : matheo.greneche0@gmail.com
**Politique de confidentialité** : https://github.com/GRENECHE-Matheo/GoodLife/blob/main/PRIVACY.md
(URL publique, sans PDF — conforme).

## 4. Contenu de l'application (Play Console › Règles › Contenu de l'appli)

| Formulaire | Réponse |
|---|---|
| Politique de confidentialité | L'URL ci-dessus |
| Publicités | Non, aucune publicité |
| Accès à l'appli | Tout est accessible sans compte ni identifiant |
| Public cible | 13–15 ans (15 ans seulement), 16–17 ans, 18 ans et plus. **Pas** de moins de 13 ans → pas concerné par le programme Familles |
| Classification du contenu | Questionnaire IARC : pas de violence, pas de contenu sexuel, pas de jeux d'argent ; mentionner les conseils nutritionnels |
| Applications de santé | Remplir la **déclaration** : suivi nutritionnel, suivi du sommeil, bien-être ; **pas** un dispositif médical ; pas d'accès à Health Connect |
| Contenu généré par IA | L'app utilise l'IA (Gemini) pour analyser des photos de repas, proposer des idées/recettes et discuter avec le coach. Chaque réponse est marquée « Généré par l'IA » avec un bouton « Signaler » dans l'app |
| Applis gouvernementales / financières / VPN | Non |

## 5. Sécurité des données (Data safety)

« Collecte » = données qui quittent le téléphone. Tout le reste (profil, repas, sommeil, progression)
reste **sur le téléphone** et n'est pas à déclarer comme collecté.

**Données collectées** (toutes **facultatives**, uniquement si l'utilisateur active l'IA avec sa propre clé,
envoyées directement à Google Gemini, **chiffrées en transit** (HTTPS), **pas partagées** au sens de Google
Play car l'envoi est une action de l'utilisateur qui s'y attend) :

| Type Play | Détail | Finalité |
|---|---|---|
| Photos et vidéos › Photos | Photo du repas à analyser | Fonctionnalité de l'appli |
| Santé et remise en forme › Santé | Poids, taille, allergies, repas du jour, objectif, évolution du poids (coach) | Fonctionnalité de l'appli |
| Santé et remise en forme › Remise en forme | Pas, séances de sport, nombre et distance des sorties (résumés envoyés au coach) | Fonctionnalité de l'appli |
| Informations personnelles › Autres | Âge, sexe | Fonctionnalité de l'appli |

- **Traitement éphémère** : non (Google peut conserver temporairement, voir ses conditions).
- **Suppression** : les données locales s'effacent dans Paramètres › Effacer toutes mes données ; il n'y a
  aucun compte ni serveur GoodLife.
- **Code-barres → Open Food Facts** : seul le numéro d'un produit est envoyé ; ce n'est pas une donnée
  personnelle (à ne pas déclarer, mais c'est expliqué dans la politique).
- **Sauvegarde chiffrée** : fichier écrit par l'utilisateur à l'endroit qu'il choisit, chiffré avec son mot de
  passe ; le développeur n'y a pas accès.
- **Signalement IA** : e-mail rédigé et envoyé par l'utilisateur lui-même depuis sa messagerie.
- **Amis (Tap to Sync, QR, croisements)** : si le profil est public, le pseudo et ce que l'utilisateur coche (niveau, série,
  liste du Nutridex) partent **directement vers le téléphone d'un autre utilisateur**, sans serveur. Par prudence, déclarer
  comme données **partagées, facultatives, à l'initiative de l'utilisateur** : « Infos personnelles › Autres infos (pseudo) »
  et « Activité dans l'appli › Autres actions (niveau, série, aliments découverts) ».
- **Localisation / tracés GPS** : restent sur le téléphone → pas « collectés ». La carte (OpenFreeMap) et la recherche de
  clubs (Overpass) reçoivent la **zone affichée** : déclarer « Localisation approximative », facultative, fonctionnalité
  de l'appli, non partagée à des fins publicitaires.
- **Pas** : lus sur le téléphone (capteur ou Health Connect), jamais transmis → pas « collectés » ; seule la moyenne peut partir
  vers Gemini si l'utilisateur demande un objectif à l'IA (déjà couvert par « Santé et remise en forme »).
- Sleep API : calculée par les services Google Play sur le téléphone ; GoodLife ne transmet rien.
- **Notifications du coach** : préparées et programmées sur le téléphone (AlarmManager), sans serveur → rien à déclarer.
- **Actus du jour** : l'app **lit** des flux RSS publics (franceinfo, Sciences et Avenir, Futura, Anses, Santé publique
  France) ; aucune donnée de l'utilisateur n'est envoyée (seulement l'adresse IP, comme pour tout site) → rien à déclarer.
- **Déclaration « Applications d'actualités »** (Contenu de l'appli) : répondre **non**, GoodLife n'est pas une appli
  d'actualités (les actus sont une petite rubrique). Pour la presse, seuls le titre et le lien sont repris (droits voisins).

## 6. Autorisations sensibles

| Permission | Justification |
|---|---|
| `CAMERA` | Photo du repas et scan de code-barres (dans l'app) |
| `ACTIVITY_RECOGNITION` | Détection du sommeil (Sleep API) et compteur de pas, seulement si activés |
| `health.READ_STEPS` | Lecture seule des pas dans Health Connect, si l'utilisateur choisit cette source |
| `RECEIVE_BOOT_COMPLETED` | Réactiver sommeil, croisements et les rappels du coach après un redémarrage |
| `INTERNET` | IA (si activée), Open Food Facts, carte, actus du jour (flux RSS publics) |
| `NFC` | Tap to Sync : échange de cartes entre amis en collant les téléphones |
| `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` | Croisements entre joueurs (Android 12+, 18+, désactivé par défaut) |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Service des croisements avec sa notification (discrète) |
| `POST_NOTIFICATIONS` | Notifications du coach (acceptées par l'utilisateur), amis (encouragements, rencontres), croisements et activités GPS |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Activités GPS (course, marche, vélo), seulement pendant une activité lancée par l'utilisateur |
| `FOREGROUND_SERVICE_LOCATION` | Suivi GPS écran éteint pendant l'activité, avec notification |

Pas de localisation en arrière-plan (`ACCESS_BACKGROUND_LOCATION`), ni de contacts, de SMS ou de stockage.

**Déclarations supplémentaires dans la Play Console :**
- **Health Connect** : formulaire d'accès aux données Health Connect pour `READ_STEPS` — usage : afficher les pas du jour,
  l'objectif de pas et l'XP liée aux pas ; lecture seule ; données jamais transmises.
- **Localisation** : déclaration « pendant l'utilisation » seulement ; usage = suivi des activités sportives (tracé, distance,
  vitesse, dénivelé) stocké sur le téléphone.
- **Service de premier plan** (`location`) : suivi GPS d'une activité démarrée par l'utilisateur, notification permanente
  pendant l'activité ; **vidéo** demandée (démarrer une course, écran éteint, notification visible, arrêt).
- **Service de premier plan** (`connectedDevice`) : croisements, échange Bluetooth entre appareils GoodLife proches, démarré
  par l'utilisateur, notification permanente discrète (canal de faible importance). Google demande une courte **vidéo** montrant l'activation.

## 7. Déjà en place dans l'app

- Consentement explicite aux données de santé (case à cocher) au premier lancement.
- Âge minimum 15 ans ; IA réservée aux 18 ans et plus ; pas de perte de poids avant 18 ans ou si IMC < 18,5.
- Avertissement « pas un dispositif médical » à l'accueil, dans À propos et dans la politique.
- Politique de confidentialité dans l'app + contact.
- Export (portabilité), effacement, retrait du consentement IA.
- Mention « Généré par l'IA » + bouton « Signaler » sur chaque contenu d'IA.
- Amis : profil privé par défaut, partage choisi champ par champ, cartes signées, blocage, encouragements sans texte libre,
  croisements réservés aux 18+.
