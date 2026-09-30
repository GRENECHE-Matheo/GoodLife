# Politique de confidentialité — GoodLife

Version 0.3 · mise à jour le 30 septembre 2026

> Projet personnel assisté par IA. Ce document décrit honnêtement ce que fait l'application ; il ne constitue pas un avis juridique.

## Qui est responsable ?

GoodLife est un projet personnel développé par Mathéo Greneche, avec l'assistance d'une IA. L'app n'a pas de serveur et n'envoie aucune donnée à son développeur. Pour toute question : github.com/GRENECHE-Matheo.

## Ce qui reste sur ton téléphone

Ton profil (âge, sexe, poids, taille, activité, objectif, habitudes, allergies), tes repas, ton emploi du temps de repas, ton sommeil et tes réglages. Certaines de ces informations sont des données de santé. Elles sont chiffrées sur le téléphone (AES-256, Android Keystore), sans compte ni sauvegarde cloud, et ne sont envoyées nulle part tant que l'IA est désactivée. Base légale : ton consentement explicite, donné au premier lancement. Durée : tant que l'app est installée (repas : 1 an, emploi du temps : 3 mois).

## Ce qui est envoyé si tu actives l'IA

L'IA est désactivée par défaut, demande un consentement séparé (réservé aux 18 ans et plus) et ta propre clé Gemini gratuite. Si tu l'actives, seules les données nécessaires à chaque demande sont envoyées :
• Analyse de photo : la photo du repas et tes allergies.
• Objectif calorique : âge, sexe, poids, taille, activité, objectif, habitudes, allergies.
• Idées de repas : ton objectif, les repas du jour, tes habitudes et allergies.
• Recette : le nom du plat, tes habitudes et allergies.
Ne sont jamais envoyés : ton prénom, ton sommeil, ton historique complet.

## À qui ces données sont envoyées

• Google LLC (API Gemini), directement depuis ton téléphone, avec ta propre clé API et donc sous ton propre compte Google. Google traite ces données selon les conditions de l'API Gemini ; elles peuvent être conservées temporairement par Google (par exemple pour détecter les abus) et traitées hors de l'Union européenne. Google est une entreprise américaine adhérente au cadre de protection des données UE–États-Unis (Data Privacy Framework).
• GoodLife n'a aucun serveur : le développeur ne reçoit et ne voit aucune de tes données.

## Ta clé API

Ta clé Gemini est chiffrée sur le téléphone (Android Keystore), conservée lors des mises à jour de l'app et envoyée uniquement à Google, dans l'en-tête des requêtes. Elle n'apparaît dans aucune exportation.

## Scan de code-barres (sans IA)

Le code-barres est lu sur le téléphone avec ML Kit (modèle intégré, aucune image envoyée). Seul le numéro du code-barres est envoyé à Open Food Facts (association française, base de données ouverte) pour obtenir les valeurs nutritionnelles. Open Food Facts voit ton adresse IP, comme n'importe quel site web.

## Vérification des mises à jour

Si l'option est activée (Paramètres › Mises à jour), l'app demande au plus toutes les 12 h à GitHub quelle est la dernière version publiée. GitHub voit alors ton adresse IP. Aucune autre donnée n'est envoyée. L'app n'installe rien toute seule : elle ouvre la page officielle de téléchargement.

## Retirer ton consentement

Tu peux désactiver l'IA à tout moment dans Paramètres › Intelligence artificielle. Plus rien n'est alors envoyé. Ce retrait ne remet pas en cause les demandes faites avant.

## Le sommeil

La détection automatique utilise la Sleep API des services Google Play, calculée sur le téléphone. GoodLife ne reçoit que les heures de coucher et de réveil, stockées localement et jamais envoyées.

## Tes droits (RGPD)

Accès et portabilité : Paramètres › Exporter mes données. Rectification : Profil › Modifier mes infos. Effacement : Paramètres › Effacer toutes mes données (ou désinstaller l'app). Retrait du consentement : interrupteur IA. Comme GoodLife n'a pas de serveur, toutes tes données sont sur ton téléphone. Tu peux aussi adresser une réclamation à la CNIL (cnil.fr).

## Ce que GoodLife ne fait pas

Pas de publicité, pas de revente de données, pas de traceur, pas de profilage marketing.

## Important

Les calories et conseils donnés par l'IA sont des estimations indicatives. GoodLife n'est pas un dispositif médical et ne remplace pas l'avis d'un professionnel de santé.
