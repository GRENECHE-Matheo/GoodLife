# Publier Lifoody sur Google Play — aide-mémoire

> Préparé avec l'assistance d'une IA, à partir des règles publiées par Google au 1er octobre 2026.
> Ce n'est pas un avis juridique : relis chaque réponse dans la Play Console, les formulaires évoluent.

## 1. Le fichier à envoyer

- Format **AAB** (obligatoire) : artefact `GoodLife-play-aab` (nom technique conservé) produit par GitHub Actions
  (`gradle bundlePlayRelease`). C'est la version **play** : sans mise à jour intégrée ni permission
  `REQUEST_INSTALL_PACKAGES` (interdites sur Play pour se mettre à jour soi-même).
- Cible **Android 16 (API 36)** : exigé pour toute nouvelle app depuis le 31/08/2026.
- **Pages mémoire de 16 Ko** (exigé pour les apps ciblant Android 15+) : vérifié en v0.11.0, toutes les bibliothèques
  natives (carte, caméra, codes-barres) sont alignées sur 16 Ko. À revérifier après chaque mise à jour de bibliothèque :
  Play Console › App bundle explorer, ou `zipalign -c -P 16 -v 4 app.apk`.
- **Signature (Play App Signing)** : à la création de l'app, choisir d'utiliser **ta propre clé**
  (celle de `GOODLIFE_KEYSTORE`) si tu veux que les utilisateurs de l'APK GitHub puissent passer à la
  version Play sans désinstaller. Sinon, Google crée sa propre clé et les deux versions ne pourront pas
  se mettre à jour l'une par l'autre. Ne jamais perdre ni changer la clé.

## 2. Compte développeur

- Compte personnel créé après novembre 2023 : Google impose un **test fermé avec au moins 12 testeurs
  pendant 14 jours** avant la mise en production.
- Vérification d'identité demandée par Google.
- Statut de « professionnel » (DSA, UE) : Lifoody est gratuit, sans pub ni achat → déclarer
  **non-professionnel** si c'est un projet personnel sans but commercial.

## 3. Fiche Play Store

**Catégorie** : Santé et remise en forme.

**À mettre dans la description (obligatoire pour les applis santé)** :

> Lifoody est une application de bien-être. Ce n'est pas un dispositif médical : elle ne permet pas de
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
| Applications de santé | Remplir la **déclaration** : suivi nutritionnel, suivi du sommeil, bien-être ; **pas** un dispositif médical ; Health Connect en **lecture seule des pas** (voir § 6) |
| Contenu généré par IA | L'app utilise l'IA (Gemini) pour analyser des photos de repas, proposer des idées/recettes et discuter avec le coach. Chaque réponse est marquée « Généré par l'IA » avec un bouton « Signaler » dans l'app |
| Applis gouvernementales / financières / VPN | Non |

## 5. Sécurité des données (Data safety)

« Collecte » = données qui quittent le téléphone. Tout le reste (profil, repas, sommeil, progression)
reste **sur le téléphone** et n'est pas à déclarer comme collecté.

**Données collectées** (chiffrées en transit, HTTPS). Les lignes « IA » sont **facultatives** et ne partent que si
l'utilisateur active l'IA avec sa propre clé, directement vers Google Gemini (**pas partagées** au sens de Google Play,
car l'envoi est une action de l'utilisateur qui s'y attend). Les autres lignes sont précisées dans la colonne Détail :

| Type Play | Détail | Finalité |
|---|---|---|
| Photos et vidéos › Photos | Photo du repas à analyser | Fonctionnalité de l'appli |
| Santé et remise en forme › Santé | Poids, taille, allergies, repas du jour, objectif, évolution du poids (coach) | Fonctionnalité de l'appli |
| Santé et remise en forme › Remise en forme | Pas (du jour, de la semaine, d'hier pour l'objectif d'eau, des 7 derniers jours pour l'objectif de pas), séances de sport, limites ou douleurs écrites dans le programme, nombre et distance des sorties (IA) | Fonctionnalité de l'appli |
| Informations personnelles › Autres | Âge, sexe (IA) | Fonctionnalité de l'appli |
| Activité dans l'appli › Autres contenus générés par l'utilisateur | Questions écrites au coach et à l'IA (IA) | Fonctionnalité de l'appli |
| Localisation › Approximative | Zone de la carte affichée ou choisie (carte, itinéraires, altitude, recherche de lieu, clubs, cartes hors ligne) — **sans IA**, facultatif (seulement en utilisant la carte) | Fonctionnalité de l'appli |
| Infos et performances de l'appli › Diagnostics | ML Kit (lecteur de code-barres et de QR de Google) : modèle et système du téléphone, version de l'appli, mesures de performance — collecté par le SDK de Google, **sans IA** | Analyse (par Google, pour ML Kit) |
| Appareil ou autres identifiants | Identifiant d'installation envoyé par ML Kit à Google (diagnostics) — **sans IA** | Analyse (par Google, pour ML Kit) |

- **Traitement éphémère** : non (Google peut conserver temporairement, voir ses conditions).
- **Suppression** : les données locales s'effacent dans Paramètres › Effacer toutes mes données ; il n'y a
  aucun compte ni serveur Lifoody.
- **Code-barres → Open Food Facts** : seul le numéro d'un produit est envoyé ; ce n'est pas une donnée
  personnelle (à ne pas déclarer, mais c'est expliqué dans la politique).
- **Sauvegarde chiffrée** : fichier écrit par l'utilisateur à l'endroit qu'il choisit, chiffré avec son mot de
  passe ; le développeur n'y a pas accès.
- **Signalement IA** : e-mail rédigé et envoyé par l'utilisateur lui-même depuis sa messagerie.
- **Amis (Tap to Sync, QR, croisements)** : si le profil est public, le pseudo et ce que l'utilisateur coche (niveau, série,
  liste du Nutridex, bilan de la semaine : jours validés, total de pas, XP) et les encouragements partent **directement
  vers le téléphone d'un autre utilisateur**, sans serveur. Par prudence, déclarer comme données **partagées, facultatives,
  à l'initiative de l'utilisateur** : « Infos personnelles › Autres infos (pseudo) », « Activité dans l'appli › Autres
  actions (niveau, série, aliments découverts, encouragements) » et « Santé et remise en forme › Remise en forme (pas de
  la semaine) ».
- **Localisation / tracés GPS** : la position exacte et les tracés restent sur le téléphone. La carte et les itinéraires
  (tuiles OpenFreeMap), le dénivelé (tuiles d'altitude « Terrain Tiles » sur Amazon S3), la recherche de lieu (Nominatim)
  et la recherche de clubs (Overpass, aussi en secours pour les itinéraires) reçoivent la **zone concernée** (et le texte
  tapé pour la recherche) : c'est la ligne « Localisation › Approximative » du tableau, non partagée à des fins publicitaires.
- **Pas** : lus sur le téléphone (capteur ou Health Connect). Ils partent vers Gemini seulement avec l'IA activée (coach,
  objectif d'eau, objectif de pas : ligne « Remise en forme » du tableau) et vers les amis si le profil est public (voir Amis).
- Sleep API : calculée par les services Google Play sur le téléphone ; Lifoody ne transmet rien.
- **Notifications du coach** : préparées et programmées sur le téléphone (AlarmManager), sans serveur → rien à déclarer.
- **Actus du jour** : l'app **lit** des flux RSS publics (franceinfo, Sciences et Avenir, Futura, Anses, Santé publique
  France) ; aucune donnée de l'utilisateur n'est envoyée (seulement l'adresse IP, comme pour tout site) → rien à déclarer.
- **Déclaration « Applications d'actualités »** (Contenu de l'appli) : répondre **non**, Lifoody n'est pas une appli
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
- **Service de premier plan** (`connectedDevice`) : croisements, échange Bluetooth entre appareils Lifoody proches, démarré
  par l'utilisateur, notification permanente discrète (canal de faible importance). Google demande une courte **vidéo** montrant l'activation.

## 7. Avant de publier : vérifier les noms (marques)

Contrôle fait en ligne les 01/10 et 03/10/2026, **sans valeur juridique** : à confirmer sur [TMview](https://www.tmdn.org/tmview/)
(marques de l'UE, de la France et du monde) avant la mise en ligne.

| Nom | Ce qui existe | Risque |
|---|---|---|
| **Lifoody** (nom choisi le 03/10/2026, remplace « GoodLife ») | Aucune appli ni marque « Lifoody » trouvée (Google Play : aucun résultat ; web : seulement LYOFOOD, marque polonaise de plats lyophilisés pour la randonnée). L'ancien nom « GoodLife » était en conflit avec « GoodLife Fitness » (marque déposée au Canada, appli Android de la même catégorie). | Faible, à confirmer sur TMview et à l'INPI (classes 9, 41, 44) ; penser à déposer la marque. |
| **Nutridex** | Une marque « NUTRIDEX » existe pour des produits alimentaires (classe 30), pas pour des logiciels. Le suffixe « -dex » rappelle le Pokédex (marque de Nintendo/The Pokémon Company), mais il est très courant dans les applis. | Faible |
| **Croisements** | Ancien nom « StreetPass » retiré (marque de Nintendo). | Aucun |
| **Tap to Sync** | Aucune marque trouvée sous ce nom ; expression descriptive. | Faible |
| **Gemini, OpenStreetMap, OpenFreeMap, Open Food Facts** | Cités seulement pour dire d'où viennent les données ou l'IA, avec leurs mentions d'attribution : usage autorisé. | Aucun |

## 8. Déjà en place dans l'app

- Consentement explicite aux données de santé (case à cocher) au premier lancement.
- Âge minimum 15 ans ; IA réservée aux 18 ans et plus ; pas de perte de poids avant 18 ans ou si IMC < 18,5.
- Avertissement « pas un dispositif médical » à l'accueil, dans À propos et dans la politique.
- Politique de confidentialité dans l'app + contact.
- Export (portabilité), effacement, retrait du consentement IA.
- Mention « Généré par l'IA » + bouton « Signaler » sur chaque contenu d'IA.
- Amis : profil privé par défaut, partage choisi champ par champ, cartes signées, blocage, encouragements sans texte libre,
  croisements réservés aux 18+.
