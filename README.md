# GoodLife — v0.5

> ⚠️ **Projet assisté par IA** : le code de cette application a été écrit avec l'assistance
> d'une intelligence artificielle (Claude, par Anthropic), puis relu et piloté par son auteur.

Application Android de suivi de l'alimentation et du sommeil, avec une interface
Material You inspirée des applications Google.

## Fonctionnalités

- **Scanner un repas** : caméra intégrée à l'app (CameraX), l'IA identifie les aliments,
  estime les portions, les calories et les macronutriments.
- **Objectif calorique** : calcul Mifflin-St Jeor hors-ligne + recommandation personnalisée par l'IA
  (âge, sexe, poids, taille, activité, objectif, habitudes, allergies).
- **Idées de repas** : suggestions adaptées aux calories restantes, aux habitudes et aux allergies.
- **Alerte allergies** : l'IA signale les aliments pouvant contenir tes allergènes.
- **Suivi du sommeil** : détection automatique via la Sleep API de Google Play Services
  (mouvement, lumière, usage de l'écran) + mode manuel.
- Historique sur 7 jours (calories et sommeil).
- **Emploi du temps des repas** : planning de la semaine par créneau (petit-déjeuner, déjeuner, collation, dîner),
  « marquer comme mangé » ajoute le repas au journal.
- **Idées de repas IA** : liste courte, détails au toucher, boutons « Planifier » et « Recette ».
- **IA désactivée par défaut** : consentement explicite au démarrage et dans les Paramètres, réservé aux 18 ans et plus.
- **Progression** : score quotidien (0–100) selon la proximité avec l'objectif, séries de jours réussis
  (perte : sous l'objectif sans descendre sous 70 % ; prise : au moins l'objectif ; maintien : ±10 %),
  XP et niveaux (de « Commis » à « Légende de la cuisine »), courbes de score, de calories et de poids.
- **Réservée aux 15 ans et plus** ; objectif « Perdre du poids » non proposé avant 18 ans ni si l'IMC est
  sous 18,5 (garde-fou santé). Consentement explicite (case à cocher) pour les données de santé.
- **Quiz du chef** : 5 questions d'alimentation différentes chaque jour (banque de 60 questions, sans IA),
  présentées par le petit cuisto ; 4/5 le lendemain d'une série cassée permettent de la sauver.
  Petits sons (synthétisés dans l'app, désactivables) et vibrations sur les réponses ; en fin de quiz,
  la barre d'XP se remplit en ralentissant sur la fin, avec étincelles et confettis (animation spéciale au passage de niveau).
- **Pas** : capteur du téléphone ou Health Connect, double anneau calories/pas à l'accueil, objectif automatique
  (+10 % de la moyenne), fixé par l'utilisateur ou proposé par l'IA. Score du jour = 60 % alimentation + 40 % pas,
  série validée dès 80/100.
- **Nutridex** : 161 aliments et plats plutôt sains, débloqués quand l'IA les reconnaît sur une photo (vignette chiffrée),
  silhouettes pour ceux à découvrir, tri par catégorie.
- **Amis sans serveur** : profil privé par défaut ; cartes signées échangées par **Tap to Sync** (NFC, en collant les
  téléphones), **QR code** ou **StreetPass** (Bluetooth basse consommation, Android 12+, 18+) ; classement entre amis,
  Nutridex des amis, encouragements tout prêts, blocage.
- **Actus du jour** : une anecdote insolite et deux découvertes par jour (banque intégrée, sans réseau ni IA).
- **Espace Forme** (onglet du bas) : **Programme** sportif sur mesure par l'IA (but, niveau, matériel ou sans, envies,
  douleurs), séances cochées = +15 XP ; **Carte** : activités GPS course / marche / vélo (temps, distance, allure ou vitesse,
  dénivelé, en direct), carte qui suit la position comme un GPS, historique, « refaire ce parcours » pour battre son record,
  clubs et lieux de sport autour (OpenStreetMap : site, horaires, tarif s'il est connu) ; **Sommeil**.
  Carte MapLibre + OpenFreeMap (données © OpenStreetMap), sans clé API.
- **Demander à l'IA** : conversation sur une photo analysée, une recette ou son programme sportif (effacée à la fermeture).
- **Planning** : semaine lisible sur une ligne, génération de la semaine par l'IA avec budget et coût estimé par repas.
- **Saisie « aliment + grammes »** hors ligne avec la table **Ciqual 2025 de l'Anses** (3 341 aliments, Licence Ouverte
  Etalab 2.0, source : doi:10.57745/RDMHWY), en plus de la saisie directe en calories.
- **Sauvegarde chiffrée** (optionnelle) : copie de toutes les données dans le fichier de ton choix (Drive, Téléchargements…),
  chiffrée AES-256-GCM avec une clé tirée d'un mot de passe (PBKDF2, 310 000 itérations), mise à jour automatiquement
  quand on quitte l'app après un changement ; restauration dès le premier écran sur un nouveau téléphone.
- **Photo de profil** : choisie dans la galerie, recadrée et stockée chiffrée sur le téléphone.
- **Animations Material Motion** : fondu entre onglets, axe partagé pour les sous-écrans, anneau de calories,
  barres et courbes animées, cuisto qui réagit.
- **Responsive** : contenu centré (640 dp max), rail de navigation sur tablette/paysage, barre du bas fixe
  (masquée seulement quand le clavier est ouvert).
- **Mises à jour intégrées** : à l'ouverture, l'app détecte une nouvelle release GitHub et l'installe en un bouton,
  après avoir vérifié l'empreinte SHA-256 du fichier et que sa signature est identique à celle de l'app installée.
- **RGPD** : politique de confidentialité dans l'app (`PRIVACY.md`), export des données (JSON), effacement,
  retrait du consentement.
- **Paramètres** : thème (système / clair / sombre), couleur (Material You ou 5 couleurs), sons,
  verrouillage par empreinte et blocage des captures au choix, sauvegarde chiffrée, clé et modèle IA.

## Confidentialité

- Toutes les données (profil, repas, sommeil, clé API) sont stockées **uniquement sur le téléphone**,
  chiffrées en AES-256-GCM avec une clé gardée dans l'Android Keystore.
- Sauvegarde cloud Android désactivée. Aucun compte, aucune pub, aucun tracker. La seule copie possible est
  la sauvegarde chiffrée par mot de passe, si tu l'actives (le fichier est illisible sans le mot de passe).
- **Verrouillage par empreinte** (ou visage / code du téléphone) à l'ouverture et après 1 min
  en arrière-plan, via l'API Biometric d'Android : l'app ne voit jamais l'empreinte.
- **Captures d'écran bloquées** (FLAG_SECURE) : pas de capture, pas d'enregistrement d'écran,
  aperçu masqué dans les apps récentes. Désactivable dans le Profil.
- Seules les photos analysées et les infos nécessaires à une recommandation sont envoyées à
  l'API Google Gemini. Les photos ne sont pas enregistrées.

## IA

Chaque utilisateur utilise **sa propre clé Gemini**, qu'il crée lui-même chez Google (il accepte alors les
conditions de Google, 18 ans minimum ; l'éventuelle facturation se fait entre lui et Google). Elle est stockée
chiffrée sur son téléphone et conservée lors des mises à jour. GoodLife n'a aucun serveur : les requêtes vont
directement du téléphone à Google. Chaque réponse de l'IA est marquée « Généré par l'IA » avec un bouton
« Signaler » (e-mail préparé, relu et envoyé par l'utilisateur).

**Sans clé ni IA** : scan de code-barres avec ML Kit (sur le téléphone) + valeurs nutritionnelles
d'Open Food Facts (seul le numéro du code-barres est envoyé), saisie manuelle, planning et sommeil.

Modèle par défaut : `gemini-3.5-flash-lite` (rapide et économique). Si un modèle
disparaît, l'app bascule automatiquement sur un autre.

Chaque utilisateur crée sa clé sur https://aistudio.google.com/apikey et la colle dans
*Paramètres › Intelligence artificielle*. Aucune clé n'est intégrée dans l'app.

## Deux versions : GitHub et Google Play

Le même code donne deux versions (*product flavors*) :
- **github** : APK publié sur GitHub, avec la mise à jour intégrée vérifiée (empreinte + signature) ;
- **play** : pour Google Play, **sans** mise à jour intégrée ni permission `REQUEST_INSTALL_PACKAGES`
  (interdites par le règlement Play : les mises à jour passent par le Play Store).

## Compilation

Compilé automatiquement par GitHub Actions (`.github/workflows/build.yml`) :
- à chaque push sur `main` → APK GitHub + AAB Google Play en artefacts ;
- à chaque tag `v*` → release GitHub avec l'APK attaché (l'AAB reste en artefact, pour la Play Console).

En local : JDK 17+, Gradle 8.13, SDK 36 — `gradle assembleGithubRelease bundlePlayRelease`.

Publication sur Google Play : voir [`PLAY_STORE.md`](PLAY_STORE.md).

## Compatibilité

Android 8.0 (API 26) et plus, jusqu'aux dernières versions (Android 16 / 17).
Fonctionne sur toutes les surcouches : One UI (Samsung), HyperOS (Xiaomi), OxygenOS/ColorOS,
Pixel, etc. La détection automatique du sommeil nécessite les services Google Play.

## Stack

Kotlin · Jetpack Compose · Material 3 · CameraX · Play Services (Sleep API) · Gemini REST API.
minSdk 26 (Android 8.0) · targetSdk 36 (Android 16, exigé par Google Play depuis le 31/08/2026).

## Licence

Code propriétaire — tous droits réservés. Voir `LICENSE`.
