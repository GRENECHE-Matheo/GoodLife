# Journal des versions

Toutes les évolutions de GoodLife, de la plus récente à la plus ancienne.
Chaque version se télécharge depuis la page [Releases](https://github.com/GRENECHE-Matheo/GoodLife/releases)
(l'app propose aussi la mise à jour toute seule, dans la version GitHub).

Légende : ✨ nouveau · 🛠️ amélioré · 🐛 corrigé · 🔒 sécurité et vie privée

---

## v0.9.1 — 01/10/2026

**Vérification sécurité et droit, et résumé des articles officiels.**

- ✨ **Résumé du chef** (avec l'IA) pour les articles de l'Anses et de Santé publique France : 3 à 5 points fidèles à
  l'article, source et date citées, l'article original restant la référence. Pas pour les articles republiés d'autres médias.
- ✨ L'actu insolite est cherchée dans plus de sources (dont tout Futura), toujours publiée le jour même ou la veille.

- 🔒 **Coach** : avant la première question, il demande clairement ton accord pour envoyer tes chiffres (données de santé)
  à Google Gemini. Cet accord est retiré si tu désactives l'IA.
- 🔒 **Notifications** : si le verrouillage par empreinte est activé, aucun chiffre n'apparaît dans les notifications,
  même téléphone déverrouillé.
- 🔒 **Actus** : pour les articles de presse, seuls le titre, la source et le lien sont repris (droits voisins des
  éditeurs de presse) ; l'Anses et Santé publique France gardent un court extrait.
- 🔒 Effacer ses données coupe aussi les rappels déjà programmés ; l'export RGPD contient tes choix de notifications.
- 🐛 Le mot du chef ne parle plus de « garder ta série » quand tu n'en as pas encore.
- 🐛 Les actus de Santé publique France s'affichent (leur flux avait changé d'adresse) ; les avis administratifs de
  l'Anses (autorisations de produits) sont écartés.

## v0.9 — 01/10/2026

**Le chef devient ton coach : conseils perso, notifications et vraies actus du jour.**

- ✨ **Parler au chef** depuis l'accueil : pose-lui toutes tes questions (quoi manger, recette, sport, bilan de ta semaine).
  Il s'appuie sur tes chiffres (repas, pas, séries, sport, planning) et peut te proposer des repas : ils ne s'ajoutent
  au planning que si tu appuies sur « Ajouter » (IA avec ta clé Gemini, 18 ans et plus).
- ✨ **Le mot du chef** sur l'accueil : un petit message qui change selon ta journée, même sans IA.
- ✨ **Notifications du coach** (si tu les acceptes) : bilan de la veille le matin, repas prévu et encouragement à midi,
  rappel le soir seulement si ta série est en danger, bilan de la semaine le dimanche, félicitations aux grands paliers
  de série. Jamais plus d'une à la fois, et rien de lisible sur l'écran verrouillé.
- ✨ **Vraies actus du jour**, pour tout le monde et sans clé : alimentation et sport, tirées des flux publics de franceinfo,
  Sciences et Avenir, Futura, Anses et Santé publique France, avec une actu insolite quand il y en a une (publiée le jour
  même ou la veille). Jamais deux fois la même, pas de pub ni de sujets anxiogènes. Avec l'IA : « Demander au chef » sur une actu.
- ✨ Sans internet, 137 nouvelles anecdotes vérifiées prennent le relais, sans jamais en répéter une.
- 🛠️ **Inscription en 7 petites pages** : bienvenue, confidentialité (à accepter), toi, ton corps, ton objectif,
  récapitulatif avec ton objectif calorique, et choix des notifications.
- 🐛 Les actus changent bien à minuit, même si l'app reste ouverte.
- 🔒 Politique de confidentialité v0.9 (coach, notifications, actus).

## v0.8.1 — 30/09/2026

**Parcours en boucle, destination guidée et cartes hors ligne.**

- ✨ **Boucles** : choisis une distance, l'app propose 3 parcours qui reviennent au point de départ (en couleurs sur la carte).
- ✨ **Destination** : appui long sur la carte pour poser un point, puis « GO » en course, marche ou vélo.
- ✨ **Guidage** pendant l'activité : distance restante, alerte si tu t'écartes du parcours, message à l'arrivée.
- ✨ **Cartes hors ligne** : télécharge une zone choisie (taille estimée avant), mise à jour et suppression possibles.
- 🛠️ Les itinéraires sont calculés dans l'app sur les chemins OpenStreetMap : **jamais d'autoroute, de voie rapide ni de
  voie interdite** ; préférence pour les chemins, parcs, rues calmes et pistes cyclables ; ni trottoirs ni escaliers à vélo.
- 🛠️ Nouvelle interface de carte : sélecteur Activité · Parcours · Clubs, boutons ronds, panneau à poignée, bouton GO,
  type d'activité préféré mémorisé, cadrage automatique, clubs triés par distance.
- 🛠️ La localisation est demandée dès l'ouverture de la carte.
- 🔒 Politique de confidentialité v0.8.1 (serveur OpenStreetMap utilisé pour les itinéraires, tuiles hors ligne).

## v0.8 — 30/09/2026

**Espace Forme : programme sportif, carte GPS et clubs.**

- ✨ Nouvel onglet **Forme** (remplace Sommeil) : Programme · Carte · Sommeil.
- ✨ **Programme sportif sur mesure** par l'IA : but, niveau, jours, durée, avec ou sans matériel, envies, douleurs.
  Chaque séance cochée rapporte +15 XP (XP sport plafonnée à 40 par jour).
- ✨ **Activités GPS** course / marche / vélo en direct : temps, distance, allure ou vitesse, moyenne, dénivelé.
  La carte suit ta position comme un GPS, pause automatique, historique, « refaire ce parcours » pour battre ton record.
- ✨ **Clubs et lieux de sport** autour de toi (OpenStreetMap) : type, adresse, tarif si connu, horaires, site du club.
- ✨ **Demander à l'IA** : pose des questions sur une photo analysée, une recette ou ton programme (conversation effacée à la fermeture).
- 🔒 La clé API Gemini n'est plus jamais réaffichée une fois enregistrée (seulement ses 4 derniers caractères).
- 🐛 GPS plus fiable : positions périmées ignorées, recalage après un mauvais premier point, vitesse calculée quand le GPS renvoie 0.
- 🐛 Recherche de clubs : nouvelles tentatives automatiques si le serveur est surchargé.
- 🛠️ APK plus léger (plus de code natif x86 32 bits).

## v0.7.1 — 30/09/2026

**Saisie « aliment + grammes » et messages réseau précis.**

- ✨ Table **Ciqual 2025 de l'Anses** intégrée (3 341 aliments, hors ligne) : cherche un aliment, indique les grammes,
  l'app calcule calories et macros. La saisie directe en calories reste disponible.
- 🛠️ Erreurs réseau détaillées au lieu de « Pas de connexion internet » : accès réseau de l'app bloqué, réseau sans internet
  (DNS privé, portail Wi-Fi), site bloqué, connexion sécurisée refusée, délai dépassé, limite GitHub.
- 🛠️ Sources nutritionnelles citées dans « À propos ».

## v0.7 — 30/09/2026

**Pas, Nutridex, amis et actus du jour.**

- ✨ **Pas** : capteur du téléphone ou Health Connect, double anneau calories/pas à l'accueil, objectif automatique, manuel ou proposé par l'IA.
- ✨ **Score du jour** = 60 % alimentation + 40 % pas ; série validée dès 80/100 (les anciens jours gardent les anciennes règles).
- ✨ **Nutridex** : 161 aliments sains à débloquer en les prenant en photo, silhouettes pour ceux à découvrir, tri par catégorie.
- ✨ **Amis sans serveur** : cartes de joueur signées échangées par **Tap to Sync** (NFC), **QR code** ou **StreetPass**
  (Bluetooth, Android 12+ et 18+) ; classement entre amis, encouragements, blocage. Profil privé par défaut.
- ✨ **Actus du jour** : une anecdote insolite et deux découvertes par jour, sans réseau.
- 🛠️ Planning plus lisible ; semaine générée par l'IA avec budget et coût estimé par repas.
- 🛠️ Idées de repas par moment de la journée, avec bouton « Régénérer ».
- 🐛 Scanner : bouton de photo toujours visible, quelle que soit la taille de l'écran.

## v0.6 — 30/09/2026

**Sauvegarde chiffrée, sons et conformité.**

- ✨ **Sauvegarde chiffrée** par mot de passe dans le fichier de ton choix (Drive, Téléchargements…), mise à jour
  automatiquement, restauration dès le premier écran sur un nouveau téléphone.
- ✨ Quiz : petits sons (désactivables), vibrations, barre d'XP animée avec particules et animation de passage de niveau.
- 🛠️ Android 16 (API 36) ; deux versions : GitHub (mise à jour intégrée) et Google Play.
- 🔒 Réponses de l'IA marquées « Généré par l'IA » avec un bouton « Signaler ».
- 🔒 15 ans minimum, consentement explicite aux données de santé, pas d'objectif « perte de poids » avant 18 ans ou si l'IMC est sous 18,5.
- 🔒 Le verrouillage ne se lève plus si le capteur d'empreinte est momentanément indisponible.
- 🐛 XP d'un jour rattrapé, pluriels, graphiques rognés, couleurs en mode sombre.

## v0.5 — 30/09/2026

**Animations et écrans adaptés à toutes les tailles.**

- ✨ Animations Material Motion : fondu entre onglets, transitions des sous-écrans, anneaux et courbes animés, cuisto qui réagit.
- ✨ Tablette et paysage : rail de navigation sur le côté, contenu centré.
- ✨ Écran « Ce qui quitte ton téléphone » : transparence sur les données envoyées.
- 🛠️ Barre du bas fixe (masquée seulement quand le clavier est ouvert) ; toucher sa photo ouvre le profil.

## v0.4 — 30/09/2026

**Séries, XP et quiz du chef.**

- ✨ **Score quotidien** (0–100) et **séries** de jours réussis.
- ✨ **XP et niveaux**, de « Commis » à « Légende de la cuisine ».
- ✨ **Quiz du chef** : 5 questions d'alimentation par jour ; un bon score permet de sauver une série cassée.
- ✨ Courbes de score, de calories et de poids ; pesées ; photo de profil.

## v0.3.1 — 30/09/2026

- ✨ Mise à jour intégrée : téléchargement vérifié (empreinte SHA-256 et signature) et installation en un bouton.

## v0.3 — 30/09/2026

**Clé personnelle, code-barres et planning.**

- ✨ **Scan de code-barres** sans IA (ML Kit, sur le téléphone) avec les valeurs d'Open Food Facts.
- ✨ **Emploi du temps des repas** sur la semaine et **recettes**.
- ✨ Détection des nouvelles versions.
- 🔒 Chaque utilisateur utilise **sa propre clé Gemini** : le relais de la v0.2 est supprimé, GoodLife n'a plus aucun serveur.
- 🔒 IA désactivée par défaut, consentement explicite ; politique de confidentialité, export et effacement des données (RGPD).

## v0.2.1 — 30/09/2026

- 🐛 Correctif de connexion au relais IA (retiré en v0.3).

## v0.2 — 30/09/2026

- ✨ Relais IA pour essayer l'app sans clé (retiré dès la v0.3 au profit de la clé personnelle).

## v0.1 — 30/09/2026

**Première version.**

- ✨ Scanner un repas : l'IA reconnaît les aliments et estime portions, calories et macros.
- ✨ Objectif calorique calculé sur le téléphone, affiné par l'IA si tu veux.
- ✨ Idées de repas et alerte allergies.
- ✨ Suivi du sommeil automatique (Sleep API) ou manuel ; historique sur 7 jours.
- 🔒 Données chiffrées sur le téléphone uniquement, verrouillage par empreinte, captures d'écran bloquées.
