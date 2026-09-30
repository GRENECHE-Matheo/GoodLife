# GoodLife — v0.3

> ⚠️ **Projet assisté par IA** : le code de cette application a été écrit avec l'assistance
> d'une intelligence artificielle (Claude, par Anthropic), puis relu et piloté par son auteur.

Application Android de suivi de l'alimentation et du sommeil, avec une interface
Material You inspirée des applications Google.

## Fonctionnalités (v0.1)

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
- **Mises à jour** : l'app signale les nouvelles versions publiées sur GitHub et ouvre la page officielle.
- **RGPD** : politique de confidentialité dans l'app (`PRIVACY.md`), export des données (JSON), effacement,
  retrait du consentement.
- **Paramètres** : thème (système / clair / sombre), couleur (Material You ou 5 couleurs),
  verrouillage par empreinte et blocage des captures au choix, clé et modèle IA.

## Confidentialité

- Toutes les données (profil, repas, sommeil, clé API) sont stockées **uniquement sur le téléphone**,
  chiffrées en AES-256-GCM avec une clé gardée dans l'Android Keystore.
- Sauvegarde cloud Android désactivée. Aucun compte, aucune pub, aucun tracker.
- **Verrouillage par empreinte** (ou visage / code du téléphone) à l'ouverture et après 1 min
  en arrière-plan, via l'API Biometric d'Android : l'app ne voit jamais l'empreinte.
- **Captures d'écran bloquées** (FLAG_SECURE) : pas de capture, pas d'enregistrement d'écran,
  aperçu masqué dans les apps récentes. Désactivable dans le Profil.
- Seules les photos analysées et les infos nécessaires à une recommandation sont envoyées à
  l'API Google Gemini. Les photos ne sont pas enregistrées.

## IA

Chaque utilisateur utilise **sa propre clé Gemini** (gratuite), stockée chiffrée sur son téléphone et conservée
lors des mises à jour. GoodLife n'a aucun serveur : les requêtes vont directement du téléphone à Google.

**Sans clé ni IA** : scan de code-barres avec ML Kit (sur le téléphone) + valeurs nutritionnelles
d'Open Food Facts (seul le numéro du code-barres est envoyé), saisie manuelle, planning et sommeil.

Modèle par défaut : `gemini-3.5-flash-lite` (rapide, gros quota gratuit). Si un modèle
disparaît, l'app bascule automatiquement sur un autre.

L'app utilise l'API **Google Gemini** (offre gratuite de Google AI Studio). Chaque utilisateur
crée sa clé gratuite sur https://aistudio.google.com/apikey et la colle dans *Profil › Clé IA*.
Aucune clé n'est intégrée dans l'APK.

## Compilation

L'APK est compilé automatiquement par GitHub Actions (`.github/workflows/build.yml`) :
- à chaque push sur `main` → APK en artefact ;
- à chaque tag `v*` → release GitHub avec l'APK attaché.

En local : Android Studio (JDK 17, SDK 34), ou `gradle assembleRelease` avec Gradle 8.9.

## Compatibilité

Android 8.0 (API 26) et plus, jusqu'aux dernières versions (Android 16 / 17).
Fonctionne sur toutes les surcouches : One UI (Samsung), HyperOS (Xiaomi), OxygenOS/ColorOS,
Pixel, etc. La détection automatique du sommeil nécessite les services Google Play.

## Stack

Kotlin · Jetpack Compose · Material 3 · CameraX · Play Services (Sleep API) · Gemini REST API.
minSdk 26 (Android 8.0) · targetSdk 34.

## Licence

Code propriétaire — tous droits réservés. Voir `LICENSE`.
