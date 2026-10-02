# Journal des versions

Toutes les évolutions de GoodLife, de la plus récente à la plus ancienne.
Chaque version se télécharge depuis la page [Releases](https://github.com/GRENECHE-Matheo/GoodLife/releases)
(l'app propose aussi la mise à jour toute seule, dans la version GitHub).

Légende : ✨ nouveau · 🛠️ amélioré · 🐛 corrigé · 🔒 sécurité et vie privée

---

## v0.12.0 — 02/10/2026

**L'app au quotidien : un vrai frigo, un chef qui agit, un score plus juste et des widgets refaits.**

- ✨ **Mon frigo** : l'inventaire de ce que tu as à la maison, rangé par rayon, avec + et − pour ajuster en un geste.
  On le remplit à la main, depuis la liste de courses (« Ranger les cochés dans le frigo »), en photographiant son
  **ticket de caisse** (l'IA liste les aliments, tu valides), ou simplement en le disant au chef. « Que cuisiner ? »
  part maintenant de ton frigo.
- ✨ **Option : retirer du frigo après une photo de repas** (désactivée par défaut) : le chef propose ce qui a été
  utilisé, tu valides ou tu choisis « Pas mangé chez moi », et l'accueil propose d'annuler.
- ✨ **Le chef agit sur l'app** : quand tu lui parles, il peut proposer de mettre à jour ton frigo, de changer les repas
  d'un jour (« Remplacer ») ou de modifier ton programme sportif. Rien n'est appliqué sans ton appui sur le bouton.
- ✨ **Planning** : aperçu repas par repas avant de valider (avec une croix pour retirer un repas proposé),
  **« Changer ce jour »**, **« Vider la semaine »**, et les repas passés jamais validés sont retirés tout seuls.
- 🛠️ **Prix actuels** : une fois par mois, l'IA cherche sur Google les prix moyens en supermarché (sans aucune donnée
  personnelle) pour estimer le planning ; chaque recette donne le prix de ses ingrédients et le prix au kilo.
- ✨ **Photo : on corrige le poids, pas les calories.** Chaque aliment reconnu a son poids en grammes ; en le changeant,
  ses calories et le total se recalculent.
- ✨ **Boissons** : l'eau, le thé et le café sans sucre photographiés s'ajoutent au suivi de l'eau ; les autres boissons
  comptent en calories. Une photo d'eau seule ajoute juste l'eau.
- 🛠️ **Score du jour enrichi** (à partir d'aujourd'hui, les jours passés gardent leur score) : calories sur 60 points,
  protéines sur 25 et repas répartis dans la journée sur 15. C'est lui qui fait avancer la série.
- 🛠️ **Gels de série visibles** dans « Mes progrès », avec la règle pour en gagner et le prochain gel.
- 🛠️ **Sport sans triche** : chaque séance du programme se fait avec un chrono ; l'XP dépend du temps passé (rien sous
  5 minutes, au plus la durée prévue). Le programme peut aussi être supprimé.
- ✨ **Les deux widgets refaits** : anneau des calories autour du chef, série, pas, eau, protéines, prochain repas du
  planning, et boutons « Photo » et « + Eau ». Ils s'adaptent à leur taille et au thème sombre, et ne montrent aucun
  chiffre si l'app est verrouillée.
- 🛠️ **Plus facile à trouver** : actus en haut de l'accueil, carte « Mon suivi » (calories, pas, poids) qui ouvre le bon
  graphique, bouton « Me peser » ; dans les graphiques, **touche un point pour voir sa valeur**.
- 🛠️ Badges en grille qui remplit toute la largeur de l'écran, quel que soit le téléphone.
- 🛠️ Sous la conversation avec le chef, une seule ligne au lieu d'un paragraphe (le détail reste à un appui).

## v0.11.0 — 01/10/2026

**Itinéraires bien plus rapides, nouvelle icône, et un grand contrôle avant Google Play.**

- 🛠️ **Boucles et itinéraires bien plus rapides et plus fiables.** Les rues sont lues dans les tuiles de la carte
  (OpenFreeMap, servies par un réseau de cache rapide) au lieu du serveur public Overpass, souvent saturé (10 à 20 s
  d'attente, ou une erreur). Elles sont préparées dès l'ouverture du panneau des boucles et gardées en cache :
  sur l'émulateur, la réponse arrive en moins d'une seconde après l'appui. Overpass reste en secours.
- 🛠️ Calcul plus rapide : même algorithme (A*, qui donne le même chemin que Dijkstra en explorant moins de rues),
  avec des données compactes et les directions des boucles cherchées en parallèle.
- ✨ **Nouvelle icône** : fond vert aux couleurs de GoodLife, feuille bien centrée, et version « à thème »
  pour Android 13 et plus. Les notifications utilisent la même feuille.
- 🛠️ **Caméra en paysage** : l'aperçu prend toute la hauteur et le déclencheur passe sur le côté (avant, l'aperçu
  était minuscule). Les boutons des modes passent à la ligne au lieu de couper les mots.
- 🛠️ **Accueil en paysage** : l'anneau des calories à gauche, le détail à droite, le bouton Scanner reste visible.
- 🔒 **Invitation par lien : l'app demande avant d'ajouter la personne.** Avant, un lien ouvert depuis n'importe
  quelle page web ajoutait l'ami tout seul.
- 🔒 Croisements : au plus 4 notifications de rencontre par heure, même si quelqu'un fabrique plein d'identités.
- 🔒 Lectures plafonnées (réponses des serveurs, fichier de sauvegarde) pour éviter un plantage par manque de mémoire ;
  la petite vue web des suggestions Google n'a plus accès aux fichiers et n'ouvre que des liens web.
- 🛠️ **Compatible avec les téléphones à pages mémoire de 16 Ko** (exigé par Google Play) : la bibliothèque caméra
  (CameraX 1.4) est mise à jour ; toutes les bibliothèques natives sont maintenant alignées.
- 🐛 Téléphones sans services Google : le scanner de QR d'ami ne plante plus, il propose le lien d'invitation.

## v0.10.1 — 01/10/2026

- 🐛 **La toque du chef est bien posée sur sa tête** (elle passait derrière) : dans l'app, les notifications, les widgets
  et l'aperçu du widget.

## v0.10.0 — 01/10/2026

**Une app plus légère, sans rien changer à l'écran.**

- 🛠️ **APK plus léger : 68 Mo → 55 Mo.** Le code de l'app est optimisé par R8, l'outil officiel d'Android : le code
  jamais utilisé est retiré (16 Mo → 3 Mo ; une fois installé, 55 Mo → 7 Mo de code).
- 🛠️ Les ressources inutilisées et les traductions des bibliothèques dans des langues que l'app ne parle pas sont
  retirées (l'app reste en français et en anglais).
- Aucun changement visible : mêmes écrans, mêmes fonctions, mêmes données (vérifié écran par écran).
- 🔒 **Règles de sécurité de l'IA**, envoyées avec chaque demande et prioritaires : jamais de conseil médical ou
  dangereux (régime sous 1 200 kcal, jeûne prolongé, coupe-faim…), allergies toujours respectées, et les consignes
  cachées dans une photo, un article, une recherche ou un texte écrit dans l'app sont ignorées.
- ✨ Si on demande au chef ses consignes internes, il refuse avec humour (c'est le secret du chef !) et propose son aide.
- ✨ **Historique des conversations avec le coach** : gardé chiffré sur le téléphone (sans les photos), pour reprendre
  une conversation là où elle s'était arrêtée. Désactivable, et chaque conversation peut être supprimée.
- 🔒 Politique de confidentialité v0.10.0 (historique des conversations du coach).
- 🐛 Le chat de l'IA répond bien en anglais quand l'app est en anglais.

## v0.9.9 — 01/10/2026

**Un coach qui voit tes photos, l'eau à ta mesure et des courses qui se font toutes seules.**

- 🛠️ **Bouton retour** : depuis Scanner, Planning, Forme ou Profil, le retour du téléphone ramène à l'accueil
  (seul l'accueil ferme l'app).
- ✨ **Photos dans la conversation avec le coach** : un aliment, un plat, une étiquette… le chef analyse et conseille.
  La photo est envoyée à Gemini avec ton message, jamais gardée.
- ✨ **Liste de courses par le coach** : demande-lui une liste, un bouton l'ajoute à ta liste de courses.
- ✨ **Planning de la semaine** : un champ « Précisions » (ex. quelqu'un qui n'aime pas un aliment), retenu pour la
  prochaine fois, et la **liste de courses se remplit toute seule** avec la semaine (case cochée par défaut).
- ✨ **Objectif d'eau calculé chaque jour par l'IA** (si elle est activée) selon tes besoins et ton activité d'hier ;
  sinon, objectif fixe au choix.
- 🛠️ **En-tête de l'accueil** : le chef ouvre la conversation avec le coach, et un bouton 🧠 lance le quiz du jour
  (avec un point tant qu'il n'est pas fait).
- 🛠️ **Nutridex** : un aliment débloqué se colorie dans la grille ; ta photo s'affiche quand tu le touches.
- 🛠️ **Mises à jour** : vérifiées à chaque ouverture de l'app (une petite requête) et toujours affichées en haut de
  l'accueil tant qu'elles ne sont pas installées.
- 🔒 Politique de confidentialité v0.9.9 (photos au coach, objectif d'eau, précisions du planning).

## v0.9.8 — 01/10/2026

**Audit de sécurité de la clé API.**

- 🔒 La clé ne peut plus apparaître dans aucun texte de l'app (même un texte technique interne), ni dans un message
  d'erreur renvoyé par Google.
- 🔒 Les requêtes vers Google ne suivent plus les redirections : la clé ne peut pas être renvoyée vers une autre adresse.
- 🔒 Le nom du modèle d'IA est vérifié avant chaque requête : l'adresse ne peut viser que l'API Gemini de Google.
- 🐛 Lien d'invitation : bonne adresse GitHub Pages pour ouvrir le lien directement dans l'app.

## v0.9.7 — 01/10/2026

**Ta clé API blindée, un export protégé et des invitations en un clic.**

- 🔒 **Clé API dans un coffre** : chiffrée par une clé de la puce de sécurité du téléphone (StrongBox quand il y en a une),
  utilisable seulement téléphone déverrouillé, impossible à extraire. Même avec les fichiers de l'app, personne ne peut
  la lire. « Effacer toutes mes données » détruit aussi la clé du coffre.
- 🔒 **Une fois enregistrée, la clé n'est plus jamais affichée, même en partie.** Le clavier ne l'apprend pas (elle ne
  peut pas réapparaître en suggestion) et elle est retirée du presse-papiers si elle a été collée.
- 🔒 **Changer de clé sans laisser de trace** : « Remplacer la clé » propose seulement d'en taper une nouvelle (l'ancienne
  n'est jamais montrée). À l'enregistrement, ou avec « Retirer ma clé », la clé du coffre est détruite dans la puce et
  recréée : l'ancienne clé API devient illisible pour toujours, même si une copie chiffrée traînait encore.
- 🔒 **Réseau** : HTTPS uniquement, et aucun certificat ajouté à la main n'est accepté (pas d'interception par un proxy).
- 🔒 La clé de la sauvegarde chiffrée rejoint le même coffre.
- ✨ La clé peut être **limitée à GoodLife** dans la console Google Cloud (restriction « Applications Android ») :
  l'app envoie désormais son nom de paquet et l'empreinte de son certificat.
- ✨ **Export protégé par mot de passe** (recommandé) : fichier chiffré, restaurable dans GoodLife. L'export lisible
  (JSON) reste possible.
- ✨ **Invitation d'ami en lien cliquable** : « Partager ma carte » envoie un lien https ; il ouvre une petite page
  qui lance GoodLife. La carte reste dans le lien après le « # » : elle n'est jamais envoyée au serveur.
- 🔒 Politique de confidentialité v0.9.7.

## v0.9.6 — 01/10/2026

**Plus simple à utiliser au quotidien.**

- 🛠️ **Accueil réorganisé** : les calories du jour et les boutons « Scanner » / « Saisir » en premier. Les missions de
  départ tiennent sur une ligne (à déplier) et les nouveaux badges sur une petite bannière.
- 🛠️ **Écran Amis repensé** : des cartes pour chaque ami (avec ses infos et son rang), des tuiles pour ajouter un ami
  (Tap to Sync, QR code, partager ma carte, coller un code), les personnes croisées à accepter d'un bouton « Ajouter »,
  et ton profil d'ami replié en bas avec un bouton « Modifier ».
- ✨ **Notifications d'amis** : quand un ami t'encourage (l'encouragement arrive avec sa carte, à la synchro), quand tu
  croises quelqu'un pour la première fois (avec « Ajouter en ami » directement dans la notification) et quand tu
  recroises quelqu'un (sa carte est à jour).
- 🛠️ **Croisements** : la notification permanente est réduite au minimum (silencieuse, sans icône en haut de l'écran) ;
  Android impose qu'il en reste une tant que le Bluetooth tourne en arrière-plan.
- 🔒 « StreetPass » devient **« Croisements »** : StreetPass est une marque déposée de Nintendo.
- 🛠️ **Widgets** : la série, les calories et les pas du jour (avec leurs barres), et un petit mot du chef.
- 🛠️ **Score du jour = alimentation seule.** Les pas rapportent de l'XP (+10 XP les jours où l'objectif est atteint).
  Les jours d'avant gardent leur score : aucune série n'est cassée.
- 🔒 Politique de confidentialité v0.9.6 (notifications d'amis et croisements).

## v0.9.5 — 01/10/2026

**GoodLife parle anglais.**

- ✨ **App en anglais** : sur un téléphone qui n'est pas en français, toute l'app passe en anglais (écrans, notifications du
  chef, widgets, quiz, anecdotes, Nutridex, politique de confidentialité). Réglable dans Paramètres › Langue
  (langue du téléphone, français ou anglais).
- ✨ Le quiz du chef en anglais, avec les noms anglais des 370 aliments de la table Ciqual.
- ✨ Avec l'IA, le chef, les idées de repas, les recettes et les programmes sportifs répondent dans la langue de l'app.
- 🛠️ Dates et nombres au format de la langue choisie.
- ℹ️ Restent en français : les actus (sources françaises), la recherche d'aliments de la table Ciqual et les données déjà
  enregistrées (noms de repas, programmes créés avant).

## v0.9.4 — 01/10/2026

**Courses, frigo et bien-être au quotidien.**

- ✨ **Liste de courses automatique** à partir du planning (7 prochains jours) : l'IA additionne les ingrédients et range
  par rayon, ou sans IA les ingrédients des recettes enregistrées. Cases à cocher, ajout à la main, partage aux colocs.
- ✨ **« J'ai ça dans mon frigo »** : une photo de ton frigo ou une liste, et le chef propose 3 recettes anti-gaspi,
  avec ce qui manque (à ajouter à la liste de courses en un appui) et un bouton « Planifier ».
- ✨ **Hydratation** : « + 1 verre » sur l'accueil, objectif réglable (1,5 L, 2 L ou 2,5 L) et rappel optionnel l'après-midi.
- ✨ **Humeur et énergie du jour** en deux appuis, et « Ce qui semble t'aider » : des comparaisons calculées sur tes
  propres jours (pas, sommeil), affichées seulement quand il y a assez de données.
- ✨ **Missions de départ** (14 premiers jours) et **badges** (séries, Nutridex, quiz, pas, sorties, amis, eau, gel).
- ✨ **Repas au format « 2× Banane »** : l'IA compte les aliments sur la photo, et la saisie à la main a un champ « Nombre ».
- 🔒 **Garde-fou bienveillant** : si les repas notés restent très en dessous des besoins trois jours de suite, le chef prend
  gentiment des nouvelles et indique où trouver de l'aide (sans jamais de reproche).
- 🔒 Politique de confidentialité v0.9.4 (liste de courses, frigo, eau, humeur).

## v0.9.3 — 01/10/2026

**Actus à ton goût, un chef qui cherche sur internet, et des pas qui progressent avec toi.**

- ✨ **Actus à thèmes** : choisis 0, 1 ou plusieurs thèmes (alimentation, sport, santé et bien-être, insolite, anecdote du jour).
  Aucun thème = plus d'actus, nulle part.
- ✨ **L'anecdote du jour** revient : un fait vrai et vérifié sur la nourriture, jamais deux fois le même.
- ✨ **« Demander au chef » sur une actu** : il fait lui-même une recherche Google pour savoir de quoi parle l'article,
  explique avec ses mots et cite ses sources (avec ta clé Gemini).
- ✨ **Objectif de pas par l'IA, chaque jour** : recalculé à la première ouverture de la journée à partir de tes vrais pas
  des 7 derniers jours, jamais plus de 15 % de changement d'un jour à l'autre.
- ✨ **Tes pas dès l'inscription** : capteur du téléphone ou Health Connect, objectif automatique, fixe ou conseillé par l'IA.
- 🔒 Politique de confidentialité v0.9.3 (objectif de pas quotidien, recherche Google du chef, thèmes d'actus).

## v0.9.2 — 01/10/2026

**Amis à distance, défi de la semaine, gels de série et widgets du chef.**

- ✨ **Partager ma carte** par message (WhatsApp, SMS…) : ton ami l'ouvre avec GoodLife (« Partager › GoodLife ») ou la
  colle dans Amis. Toujours sans serveur : la carte est signée par ton téléphone et ne peut pas être modifiée.
- ✨ **Défi de la semaine entre amis** : chaque semaine un défi (jours validés, pas ou XP), avec son classement.
  Les scores de tes amis arrivent à chaque échange de cartes.
- ✨ **Gels de série ❄️** : tu en gagnes un tous les 7 jours de série (2 au maximum). Si tu rates un jour, un gel
  sauve ta série : il suffit de répondre aux 10 questions du chef, quel que soit le score.
- ✨ **Révision dans le quiz** : les questions ratées reviennent à la fin (réponses remélangées) pour mieux retenir.
- ✨ **Refaire un repas** : tes favoris (⭐) et tes repas fréquents se rajoutent en un appui, avec exactement les mêmes valeurs.
- ✨ **Widgets du chef** pour l'écran d'accueil : un petit (le chef et ta série) et un grand (série, gels, score du jour,
  calories, pas et un petit mot). Le chef change de pose selon ta journée et d'un jour à l'autre (il dort la nuit !).
- 🔒 Avec le verrouillage par empreinte, les widgets n'affichent aucun chiffre de santé.
- 🔒 La mémoire du quiz et des actus est maintenant incluse dans la sauvegarde chiffrée (pas de répétition après un changement de téléphone).
- 🐛 « 1 exercice » au singulier dans le programme sportif.

## v0.9.1 — 01/10/2026

**Quiz sans fin, vérification sécurité et droit, et résumé des articles officiels.**

- ✨ **Quiz du chef sans répétition, pour des années** : en plus des 60 questions classiques, des questions fabriquées
  à partir de la table Ciqual de l'Anses (370 aliments du quotidien : « lequel a le plus de protéines ? », « combien de
  kcal dans 100 g de… ? », vrai ou faux, duels…). Réponses toujours tirées des données officielles, jamais la même
  question deux fois ; une question classique ne revient qu'après 2 ans, et au plus une tous les 15 jours.

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
- ✨ **Amis sans serveur** : cartes de joueur signées échangées par **Tap to Sync** (NFC), **QR code** ou **StreetPass** (renommé « Croisements » en v0.9.6)
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
