# Publier Lifoody sur Google Play — aide-mémoire

> Préparé avec l'assistance d'une IA, à partir des règles publiées par Google au 9 octobre 2026.
> Ce n'est pas un avis juridique : relis chaque réponse dans la Play Console, les formulaires évoluent.
> Pour le statut d'entreprise, la TVA et les cotisations, fais confirmer par l'URSSAF, ton SIE ou un comptable.

## 1. Le fichier à envoyer

- Format **AAB** (obligatoire) : artefact `GoodLife-play-aab` (nom technique conservé) produit par GitHub Actions
  (`gradle bundlePlayRelease`). C'est la version **play** : sans mise à jour intégrée ni permission
  `REQUEST_INSTALL_PACKAGES` (interdites sur Play pour se mettre à jour soi-même), avec l'abonnement (Google Play Billing)
  et l'IA via le relais.
- **Adresse du relais et numéro du projet Cloud** : passés au build (ce ne sont pas des secrets), par exemple
  `-PLIFOODY_RELAY_URL=https://lifoody-relay.<ton-sous-domaine>.workers.dev -PLIFOODY_CLOUD_PROJECT=<numéro>`, ou en
  variables d'environnement du même nom (secrets/variables GitHub Actions). Sans adresse, la version Play affiche
  « Bientôt disponible » à la place de l'IA.
- Cible **Android 16 (API 36)** : exigé pour toute nouvelle app depuis le 31/08/2026.
- **Pages mémoire de 16 Ko** (exigé pour les apps ciblant Android 15+) : vérifié en v0.11.0, toutes les bibliothèques
  natives (carte, caméra, codes-barres) sont alignées sur 16 Ko. À revérifier après chaque mise à jour de bibliothèque :
  Play Console › App bundle explorer, ou `zipalign -c -P 16 -v 4 app.apk`.
- **Signature (Play App Signing)** : à la création de l'app, choisir d'utiliser **ta propre clé**
  (celle de `GOODLIFE_KEYSTORE`) si tu veux que les utilisateurs de l'APK GitHub puissent passer à la
  version Play sans désinstaller. Sinon, Google crée sa propre clé et les deux versions ne pourront pas
  se mettre à jour l'une par l'autre. Ne jamais perdre ni changer la clé.

## 2. Compte développeur et statut professionnel

- Compte personnel créé après novembre 2023 : Google impose un **test fermé avec au moins 12 testeurs
  pendant 14 jours d'affilée** avant la mise en production (voir § 10).
- Vérification d'identité demandée par Google.
- **Statut de « professionnel » (DSA, UE)** : la version Play vend un abonnement → Lifoody est une activité commerciale.
  Il faut déclarer **professionnel (trader)**. Google affiche alors publiquement sur la fiche : nom, **adresse**,
  **téléphone**, e-mail et numéro d'immatriculation. Pour ne pas publier ton adresse personnelle : domiciliation
  d'entreprise (environ 10 à 30 €/mois) ; pour le téléphone : une deuxième ligne ou un numéro virtuel.
- **Profil marchand (Google Payments)** : obligatoire pour vendre. Nom légal, adresse, IBAN, informations fiscales.
  Le nom affiché du développeur reste « Fanix Studio ».
- **Commission de Google** : **15 %** sur les abonnements (dès le premier jour, et 15 % sur le premier million de dollars
  de revenus par an pour les autres achats). Google encaisse et reverse lui-même la TVA due par les acheteurs de l'UE :
  tu reçois le prix hors TVA moins la commission.
- **E-mail de contact public** : contact.fanixstudio@gmail.com

### Ce que tu dois faire toi-même (entreprise)

1. **Créer une micro-entreprise** (gratuit, sur [formalites.entreprises.gouv.fr](https://formalites.entreprises.gouv.fr)) :
   activité « édition de logiciels / applications mobiles » (prestation de services). Tu reçois un SIREN en quelques jours.
   Nom commercial : Fanix Studio. Étudiant : c'est compatible (vérifie seulement ta bourse et ta mutuelle).
2. **Cotisations** : un pourcentage de ce que Google te verse (environ 21 à 25 % selon l'activité retenue), déclaré
   chaque mois ou trimestre sur autoentrepreneur.urssaf.fr. Rien à payer si tu ne gagnes rien. L'ACRE peut réduire
   ce taux la première année.
3. **TVA** : en micro-entreprise tu es en franchise de TVA, mais Google Ireland te paie depuis un autre pays de l'UE :
   demande un **numéro de TVA intracommunautaire** (gratuit) à ton service des impôts des entreprises (SIE), et fais
   chaque mois la **déclaration européenne de services (DES)** sur douane.gouv.fr pour les sommes reçues. Factures et
   relevés Google : mention « Autoliquidation ». À faire confirmer par le SIE.
4. **Médiateur de la consommation** : obligatoire dès qu'on vend à des particuliers en France (environ 50 à 150 €/an,
   par exemple CM2C, Medicys, AME Conso). Son nom et son site vont dans les conditions d'abonnement (`site/conditions.html`).
5. **Banque** : un compte dédié est obligatoire si le chiffre d'affaires dépasse 10 000 € deux années de suite ; conseillé
   dès le début (un compte en ligne gratuit suffit).
6. Compléter ensuite : adresse, SIREN, téléphone et médiateur dans `marketing/site-build/build_pages.py` (mentions légales
   et conditions), puis régénérer les pages du site.

## 3. Fiche Play Store

**Catégorie** : Santé et remise en forme.

**À mettre dans la description (obligatoire pour les applis santé)** :

> Lifoody est une application de bien-être. Ce n'est pas un dispositif médical : elle ne permet pas de
> diagnostiquer, traiter, guérir ou prévenir une maladie. Consulte un professionnel de santé pour tout
> avis médical, diagnostic ou traitement.

À ajouter aussi :
- « Réservée aux 15 ans et plus. Fonctions IA réservées aux 18 ans et plus : 3 essais gratuits, puis l'abonnement
  Lifoody Premium (2,99 €/mois ou 19,99 €/an, 7 jours d'essai gratuit). Toutes les fonctions sans IA sont gratuites. »
- « Projet développé avec l'assistance d'une IA. »
- La fiche indiquera automatiquement « Achats intégrés ».

**E-mail de contact** : contact.fanixstudio@gmail.com
**Site** : https://lifoody.pages.dev
**Politique de confidentialité** : https://lifoody.pages.dev/confidentialite (même texte que PRIVACY.md ;
l'adresse GitHub https://github.com/GRENECHE-Matheo/GoodLife/blob/main/PRIVACY.md marche aussi).

## 4. Contenu de l'application (Play Console › Règles › Contenu de l'appli)

| Formulaire | Réponse |
|---|---|
| Politique de confidentialité | L'URL ci-dessus |
| Publicités | Non, aucune publicité |
| Accès à l'appli | Tout est accessible sans compte ni identifiant. Pour l'IA : 3 essais gratuits sans rien faire ; pour tester Premium, ajouter le compte de test de Google dans « Test des licences » (voir § 9) |
| Public cible | 13–15 ans (15 ans seulement), 16–17 ans, 18 ans et plus. **Pas** de moins de 13 ans → pas concerné par le programme Familles. L'abonnement ne sert qu'aux fonctions IA, réservées aux 18+ |
| Classification du contenu | Questionnaire IARC : pas de violence, pas de contenu sexuel, pas de jeux d'argent ; mentionner les conseils nutritionnels et les **achats numériques** |
| Applications de santé | Remplir la **déclaration** : suivi nutritionnel, suivi du sommeil, bien-être ; **pas** un dispositif médical ; Health Connect en **lecture seule des pas** (voir § 6) |
| Contenu généré par IA | L'app utilise l'IA (Gemini, via le serveur de Fanix Studio) pour analyser des photos de repas, proposer des idées/recettes et discuter avec le coach. Chaque réponse est marquée « Généré par l'IA » avec un bouton « Signaler » ; le serveur ajoute ses propres consignes de sécurité (pas de diagnostic ni de traitement, pas de régime dangereux, seulement alimentation, sport, sommeil et bien-être) |
| Applis gouvernementales / financières / VPN | Non |

## 5. Sécurité des données (Data safety)

« Collecte » = données qui quittent le téléphone vers le développeur ou un tiers. Tout le reste (profil, repas, sommeil,
progression) reste **sur le téléphone** et n'est pas à déclarer.

Dans la version Play, l'IA passe par le **serveur de Fanix Studio** (relais Cloudflare Workers) avant Google Gemini :
ces données sont donc **collectées par le développeur** (même si le relais ne les enregistre pas). Google Gemini (offre
payante, sous-traitant) et Cloudflare (hébergeur) traitent pour le compte de Fanix Studio : ce n'est **pas un partage**
au sens de Google Play.

**Données collectées** (toutes chiffrées en transit, HTTPS) :

| Type Play | Détail | Facultatif ? | Finalité |
|---|---|---|---|
| Photos et vidéos › Photos | Photo du repas, du frigo, du ticket, ou jointe au coach (IA) | Oui | Fonctionnalité de l'appli |
| Santé et remise en forme › Santé | Poids, taille, allergies, repas du jour, objectif, évolution du poids (IA) | Oui | Fonctionnalité de l'appli |
| Santé et remise en forme › Remise en forme | Pas (du jour, de la semaine, d'hier, des 7 derniers jours), séances, limites ou douleurs écrites dans le programme, nombre et distance des sorties (IA) | Oui | Fonctionnalité de l'appli |
| Informations personnelles › Autres | Âge, sexe (IA) | Oui | Fonctionnalité de l'appli |
| Activité dans l'appli › Autres contenus générés par l'utilisateur | Questions écrites au coach et à l'IA (IA) | Oui | Fonctionnalité de l'appli |
| Achats › Historique des achats | Jeton d'achat Google Play envoyé au relais pour vérifier l'abonnement (seuls l'état et une empreinte sont gardés) | Oui (Premium) | Fonctionnalité de l'appli, gestion du compte |
| Appareil ou autres identifiants | Identifiant aléatoire d'installation (compteurs d'essais et de limites) ; empreinte salée de l'adresse IP gardée un jour (abus des essais) ; jeton Play Integrity | Oui (IA) | Prévention des fraudes et sécurité, fonctionnalité |
| Localisation › Approximative | Zone de la carte affichée ou choisie (carte, itinéraires, altitude, recherche de lieu, clubs, cartes hors ligne) — **sans IA**, seulement en utilisant la carte | Oui | Fonctionnalité de l'appli |
| Infos et performances de l'appli › Diagnostics | ML Kit (lecteur de code-barres et de QR de Google) : modèle et système du téléphone, version de l'appli, mesures de performance — collecté par le SDK de Google | Non | Analyse (par Google, pour ML Kit) |
| Appareil ou autres identifiants | Identifiant d'installation envoyé par ML Kit à Google (diagnostics) | Non | Analyse (par Google, pour ML Kit) |

- **Traitement éphémère** : le relais ne garde rien (« éphémère » côté Fanix Studio), mais Google peut conserver
  temporairement les demandes (détection des abus) → répondre **non** pour rester prudent.
- **Suppression** : les données locales s'effacent dans Paramètres › Effacer toutes mes données (cela remplace aussi
  l'identifiant du relais) ; les compteurs du relais s'effacent seuls après 120 jours sans utilisation ; demande
  possible par e-mail. Pas de compte → pas d'URL de suppression de compte à fournir.
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
- **Pas** : lus sur le téléphone (capteur ou Health Connect). Ils partent vers le relais puis Gemini seulement avec l'IA
  (coach, objectifs d'eau et de pas : ligne « Remise en forme ») et vers les amis si le profil est public (voir Amis).
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
| `INTERNET` | IA (si activée, via le relais), Open Food Facts, carte, actus du jour (flux RSS publics) |
| `com.android.vending.BILLING` | Abonnement Lifoody Premium (ajoutée par la bibliothèque Google Play Billing, version Play seulement) |
| `NFC` | Tap to Sync : échange de cartes entre amis en collant les téléphones |
| `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` | Croisements entre joueurs (Android 12+, 18+, désactivé par défaut) |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Service des croisements avec sa notification (discrète) |
| `POST_NOTIFICATIONS` | Notifications du coach (acceptées par l'utilisateur), amis (encouragements, rencontres), croisements et activités GPS |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Activités GPS (course, marche, vélo), seulement pendant une activité lancée par l'utilisateur |
| `FOREGROUND_SERVICE_LOCATION` | Suivi GPS écran éteint pendant l'activité, avec notification |

Pas de localisation en arrière-plan (`ACCESS_BACKGROUND_LOCATION`), ni de contacts, de SMS ou de stockage.

**Déclarations supplémentaires dans la Play Console :**
- **Health Connect** : formulaire d'accès aux données Health Connect pour `READ_STEPS` — usage : afficher les pas du jour,
  l'objectif de pas et l'XP liée aux pas ; lecture seule. Les pas ne sont envoyés à l'IA (via le relais) que si
  l'utilisateur l'active, pour personnaliser ses conseils ; jamais à des fins publicitaires.
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
- Âge minimum 15 ans ; IA (donc Premium) réservée aux 18 ans et plus ; pas de perte de poids avant 18 ans ou si IMC < 18,5.
- Avertissement « pas un dispositif médical » à l'accueil, dans À propos et dans la politique.
- Politique de confidentialité dans l'app (partie GitHub ou Google Play selon la version) + contact.
- Export (portabilité), effacement, retrait du consentement IA.
- Mention « Généré par l'IA » + bouton « Signaler » sur chaque contenu d'IA.
- Amis : profil privé par défaut, partage choisi champ par champ, cartes signées, blocage, encouragements sans texte libre,
  croisements réservés aux 18+.
- Écran Premium conforme aux règles de Google sur les abonnements : prix et période lisibles, prix après l'essai,
  renouvellement automatique et résiliation expliqués avant l'achat, « Restaurer mes achats », lien « Gérer mon
  abonnement » vers Google Play, liens vers les conditions et la confidentialité.

## 9. Abonnement dans la Play Console

Possible seulement **après** avoir envoyé un AAB qui contient la bibliothèque Billing (un premier envoi en test fermé suffit).

1. Monétiser › Produits › **Abonnements** › Créer : identifiant **`lifoody_premium`** (le même que dans l'app et que
   `PRODUCT_IDS` du relais ; il ne pourra plus changer), nom « Lifoody Premium ».
2. Deux **forfaits de base** (renouvellement automatique) :
   - `mensuel` : période 1 mois, **2,99 €** (laisser Google convertir les autres pays) ;
   - `annuel` : période 1 an, **19,99 €**.
3. Une **offre** sur chaque forfait : « Essai sans frais » de **7 jours**, éligibilité « Nouveaux clients » (une seule fois
   par compte Google).
4. Réglages conseillés : délai de grâce 7 jours, suspension de compte activée (l'app considère l'abonnement actif pendant
   le délai de grâce), annulation possible à tout moment.
5. **Test des licences** (Paramètres › Test des licences) : ajoute ton compte Google et ceux des testeurs pour acheter
   sans être débité (les abonnements de test se renouvellent toutes les 5 minutes environ).
6. **Intégrité de l'appli** (Test et publication › Intégrité de l'appli) : associer le projet Google Cloud `lifoody-ia`
   (Play Integrity API). Son **numéro de projet** va dans `LIFOODY_CLOUD_PROJECT` au build.
7. **Utilisateurs et autorisations** : inviter le compte de service du relais (`lifoody-relay@…iam.gserviceaccount.com`)
   avec seulement « Afficher les données financières » et « Gérer les commandes et les abonnements ». Étapes détaillées
   dans `backend/README.md`.

## 10. Test fermé (12 testeurs, 14 jours)

**Procédure**

1. Play Console › Tester › **Test fermé** › créer un canal « Testeurs », envoyer l'AAB.
2. Testeurs : une **liste d'adresses e-mail** (comptes Google) ou un **Google Group** (plus simple : ils rejoignent seuls).
3. Copier le **lien d'inscription** (« Rejoindre sur le Web ») et l'envoyer avec le message ci-dessous.
4. Il faut **au moins 12 testeurs inscrits et ayant installé l'app, pendant 14 jours d'affilée**. Prévoir 15 à 20 personnes
   (certains abandonnent). Leur demander de garder l'app installée et de l'ouvrir de temps en temps.
5. Au bout des 14 jours : Tableau de bord › « Demander l'accès à la production », répondre aux questions sur le test
   (combien de testeurs, ce qui a été corrigé grâce à eux).

**Où trouver des testeurs** : amis et famille, Discord (serveurs de développeurs Android, salons « testeurs »),
Reddit **r/AndroidClosedTesting** et **r/TestersCommunity** (échange de tests : tu testes leurs apps, ils testent la tienne),
camarades de promo.

**Message à envoyer** (à adapter) :

> Salut ! Je lance **Lifoody**, une appli pour bien manger et bouger : photo du repas → calories, coach chef, planning
> de repas, sport et GPS, mini-jeu avec les amis. Google me demande 12 testeurs pendant 14 jours avant de pouvoir la
> publier. Tu peux m'aider ?
> 1. Rejoins le test avec ton compte Google : [lien d'inscription]
> 2. Installe Lifoody depuis le Play Store (le lien t'y amène).
> 3. Garde-la installée 14 jours et ouvre-la de temps en temps. Si tu vois un bug ou une idée, écris-moi !
> C'est gratuit, sans pub, et tes données restent sur ton téléphone. Merci beaucoup 🙏

**Version courte pour Reddit / Discord** (en anglais) :

> [Closed test] Lifoody — nutrition & fitness (photo → calories, chef coach, meal planner, GPS workouts). Need testers for
> 14 days, I'll test yours back! Join: [Google Group / opt-in link]. Free, no ads, data stays on your phone.
