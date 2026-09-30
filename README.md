# GoodLife

[![Dernière version](https://img.shields.io/github/v/release/GRENECHE-Matheo/GoodLife?label=version&color=2e7d32)](https://github.com/GRENECHE-Matheo/GoodLife/releases/latest)
[![Compilation](https://img.shields.io/github/actions/workflow/status/GRENECHE-Matheo/GoodLife/build.yml?branch=main&label=compilation)](https://github.com/GRENECHE-Matheo/GoodLife/actions)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)

> ⚠️ **Projet assisté par IA** : le code de cette application a été écrit avec l'assistance
> d'une intelligence artificielle (Claude, par Anthropic), puis relu et piloté par son auteur.

**GoodLife** est une application Android pour mieux manger, bouger et dormir : scan de repas par photo,
objectif calorique, pas, programme sportif, activités GPS, sommeil, séries et XP pour rester motivé.
Interface Material You, **aucun compte, aucune pub, aucun serveur** : tes données restent chiffrées sur ton téléphone.

---

## 📥 Télécharger

1. Ouvre la [dernière version](https://github.com/GRENECHE-Matheo/GoodLife/releases/latest) depuis ton téléphone.
2. Télécharge le fichier `GoodLife-vX.Y.apk` et ouvre-le.
3. Si Android le demande, autorise l'installation depuis ton navigateur.

Ensuite, l'app te prévient toute seule quand une nouvelle version sort et l'installe en un bouton
(après avoir vérifié l'empreinte du fichier et sa signature).

## 🆕 Nouveautés de la v0.9

- **Parler au chef** : un coach qui connaît tes chiffres et te conseille sur l'alimentation, le sport et la motivation ;
  il peut te proposer des repas à ajouter au planning d'un geste.
- **Notifications du coach** : bilan du matin, mot de midi, rappel du soir si ta série est en danger, bilan du dimanche.
- **Vraies actus du jour** sur l'alimentation et le sport, avec une actu insolite, pour tout le monde et sans clé.
- **Inscription en plusieurs pages**, plus simple et plus claire.

👉 Toutes les évolutions, version par version : **[CHANGELOG.md](CHANGELOG.md)**.

## ✨ Fonctionnalités

### 🍽️ Alimentation
- **Scanner un repas** : l'IA reconnaît les aliments sur une photo et estime portions, calories et macros.
- **Code-barres** sans IA (ML Kit sur le téléphone + Open Food Facts).
- **Saisie « aliment + grammes »** hors ligne avec la table **Ciqual 2025 de l'Anses** (3 341 aliments), ou directement en calories.
- **Objectif calorique** calculé sur le téléphone (Mifflin-St Jeor), affiné par l'IA si tu veux.
- **Idées de repas** adaptées aux calories restantes, habitudes et allergies, avec **alerte allergies**.
- **Planning de la semaine** lisible, généré par l'IA avec budget et coût estimé par repas ; « marquer comme mangé » l'ajoute au journal.
- **Demander à l'IA** : questions sur une photo analysée ou une recette.

### 🏃 Forme
- **Pas** : capteur du téléphone ou Health Connect, objectif automatique, manuel ou proposé par l'IA.
- **Programme sportif sur mesure** par l'IA (but, niveau, matériel ou sans, envies, douleurs) ; séances cochées = XP.
- **Activités GPS** course / marche / vélo : temps, distance, allure ou vitesse, dénivelé, carte qui te suit comme un GPS,
  historique et « refaire ce parcours » pour battre ton record.
- **Parcours** en boucle ou vers une destination, calculés dans l'app sur les chemins OpenStreetMap, avec guidage.
- **Clubs et lieux de sport** autour de toi : adresse, horaires, tarif s'il est connu, site du club.
- **Cartes hors ligne** par zone choisie.
- **Sommeil** : détection automatique (Sleep API de Google Play Services) ou saisie manuelle.

### 🏆 Motivation
- **Le chef, ton coach** : un mot du jour sur l'accueil, une conversation pour tout lui demander (avec l'IA), et des
  notifications motivantes si tu les acceptes (bilan du matin, midi, soir si la série est en danger, dimanche).
- **Score du jour** = 60 % alimentation + 40 % pas ; **série** validée dès 80/100.
- **XP et niveaux**, de « Commis » à « Légende de la cuisine », courbes de score, calories et poids.
- **Quiz du chef** : 5 questions par jour, qui peuvent sauver une série cassée ; sons et barre d'XP animée.
- **Nutridex** : 161 aliments sains à débloquer en les prenant en photo.
- **Actus du jour** : de vraies actus sur l'alimentation et le sport (franceinfo, Sciences et Avenir, Futura, Anses,
  Santé publique France), avec une actu insolite, jamais deux fois la même ; des anecdotes vérifiées sans internet.
  Avec l'IA : « Résumé du chef » des articles de l'Anses et de Santé publique France.

### 👥 Amis, sans serveur
- Cartes de joueur signées échangées par **Tap to Sync** (NFC, en collant les téléphones), **QR code** ou
  **StreetPass** (Bluetooth basse consommation, Android 12+ et 18+).
- Classement entre amis, Nutridex des amis, encouragements tout prêts, blocage. Profil **privé par défaut**.

### ⚙️ Confort
- Thème clair / sombre / système, couleurs Material You ou 5 couleurs au choix, sons désactivables.
- Animations Material Motion ; tablette et paysage avec rail de navigation.
- **Sauvegarde chiffrée** optionnelle dans le fichier de ton choix, restaurable sur un nouveau téléphone.

## 🔒 Confidentialité

- Toutes les données (profil, repas, sommeil, activités, clé API) sont stockées **uniquement sur le téléphone**,
  chiffrées en AES-256-GCM avec une clé gardée dans l'Android Keystore.
- Aucun compte, aucune pub, aucun tracker, sauvegarde cloud Android désactivée. La seule copie possible est
  la sauvegarde chiffrée par mot de passe (AES-256-GCM, PBKDF2 310 000 itérations), si tu l'actives.
- **Verrouillage par empreinte** (ou visage / code) à l'ouverture et après 1 min en arrière-plan ;
  **captures d'écran bloquées** au choix.
- Seules les photos analysées et les infos nécessaires à une réponse sont envoyées à l'API Google Gemini,
  et seulement si tu as activé l'IA. Les photos ne sont pas enregistrées.
- La liste complète de ce qui quitte le téléphone est dans l'app (« Ce qui quitte ton téléphone ») et dans
  la [politique de confidentialité](PRIVACY.md). Export des données (JSON), effacement et retrait du consentement à tout moment.
- Réservée aux 15 ans et plus ; objectif « Perdre du poids » non proposé avant 18 ans ni si l'IMC est sous 18,5.

## 🤖 IA

L'IA est **désactivée par défaut** et réservée aux 18 ans et plus. Chaque utilisateur utilise **sa propre clé Gemini**,
qu'il crée sur https://aistudio.google.com/apikey (il accepte alors les conditions de Google ; l'éventuelle facturation
se fait entre lui et Google) puis colle dans *Paramètres › Intelligence artificielle*. La clé est stockée chiffrée,
n'est jamais réaffichée, et aucune clé n'est intégrée dans l'app. Les requêtes vont directement du téléphone à Google.

Chaque réponse est marquée « Généré par l'IA » avec un bouton « Signaler ».
Modèle par défaut : `gemini-3.5-flash-lite` ; si un modèle disparaît, l'app bascule sur un autre.

**Sans clé ni IA**, l'app reste utile : code-barres, saisie « aliment + grammes », pas, activités GPS, parcours,
clubs, planning, sommeil, quiz, amis, actus du jour, mot du chef et notifications.

## 📦 Deux versions : GitHub et Google Play

Le même code donne deux versions (*product flavors*) :
- **github** : APK publié ici, avec la mise à jour intégrée vérifiée (empreinte + signature) ;
- **play** : pour Google Play, **sans** mise à jour intégrée ni permission `REQUEST_INSTALL_PACKAGES`
  (les mises à jour passent par le Play Store).

## 🛠️ Compilation

Compilé automatiquement par GitHub Actions (`.github/workflows/build.yml`) :
- à chaque push sur `main` → APK GitHub + AAB Google Play en artefacts ;
- à chaque tag `v*` → release GitHub avec l'APK, et les notes de version tirées de [CHANGELOG.md](CHANGELOG.md).

En local : JDK 17+, Gradle 8.13, SDK 36 — `gradle assembleGithubRelease bundlePlayRelease`.

Publication sur Google Play : voir [`PLAY_STORE.md`](PLAY_STORE.md).

## 📱 Compatibilité

Android 8.0 (API 26) et plus, jusqu'aux dernières versions (Android 16 / 17), sur toutes les surcouches
(One UI, HyperOS, OxygenOS/ColorOS, Pixel…). La détection automatique du sommeil nécessite les services Google Play.

**Stack** : Kotlin · Jetpack Compose · Material 3 · CameraX · ML Kit · Health Connect · MapLibre · Gemini REST API.
minSdk 26 · targetSdk 36.

## 📚 Sources et crédits

- Table de composition nutritionnelle **Ciqual 2025**, Anses — Licence Ouverte Etalab 2.0 (doi:10.57745/RDMHWY).
- **Open Food Facts** (code-barres) — ODbL.
- Cartes **OpenFreeMap** / **OpenMapTiles**, données © contributeurs **OpenStreetMap** (ODbL) ; clubs et chemins via l'API Overpass.
- Actus : flux RSS publics de **franceinfo**, **Sciences et Avenir**, **Futura**, de l'**Anses** et de **Santé publique France**
  (titre et lien vers l'article chez la source ; court extrait et résumé IA seulement pour les organismes publics,
  au titre de la réutilisation des informations publiques).

## Licence

Code propriétaire — tous droits réservés. Voir [`LICENSE`](LICENSE).
