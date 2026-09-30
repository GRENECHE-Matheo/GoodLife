#!/bin/sh
# Notes d'une release GitHub : section de la version dans CHANGELOG.md + lien vers le journal complet.
# Utilisation : sh .github/release-notes.sh v0.8.1
tag="$1"
awk -v t="$tag" 'index($0, "## " t " ") == 1 { f = 1; next } f && /^## / { exit } f' CHANGELOG.md
cat <<FIN

---
📥 **Installation** : télécharge \`GoodLife-$tag.apk\` ci-dessous et ouvre-le sur ton téléphone (Android 8.0 ou plus).
Si tu as déjà GoodLife, l'app te propose la mise à jour toute seule.

📋 Toutes les versions : [CHANGELOG.md](https://github.com/$GITHUB_REPOSITORY/blob/main/CHANGELOG.md)

Projet développé avec l'assistance d'une IA (Claude, Anthropic).
FIN
