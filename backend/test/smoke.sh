#!/usr/bin/env bash
# Essai de bout en bout du relais lancé en local (« npm run dev » avec .dev.vars : faux Google, fausse IA, aucun coût).
# Usage : bash test/smoke.sh [adresse]   (par défaut http://127.0.0.1:8787)
set -u
U="${1:-http://127.0.0.1:8787}"
INSTALL=$(printf '%032x' $RANDOM$RANDOM$RANDOM)
pass=0; failn=0
check() { if [ "$2" = "$3" ]; then echo "  ✓ $1"; pass=$((pass+1)); else echo "  ✗ $1 (attendu $3, obtenu $2)"; failn=$((failn+1)); fi; }
req() { curl -s -o /tmp/lifoody_body -w '%{http_code}' -X POST "$U/v1/generate" -H 'Content-Type: application/json' -H "X-Lifoody-Install: $1" ${3:+-H "X-Lifoody-Purchase: $3"} --data "$2"; }
PHOTO='{"task":"photo","request":{"contents":[{"role":"user","parts":[{"text":"Analyse"},{"inline_data":{"mime_type":"image/jpeg","data":"AAAA"}}]}],"generationConfig":{"responseMimeType":"application/json"}}}'
CHAT='{"task":"chat","request":{"contents":[{"role":"user","parts":[{"text":"Salut"}]}]}}'

echo "Santé"
check "GET /health" "$(curl -s -o /dev/null -w '%{http_code}' "$U/health")" 200

echo "Gratuit : 3 essais puis Premium demandé"
check "statut gratuit" "$(curl -s "$U/v1/status" -H "X-Lifoody-Install: $INSTALL" | grep -o '"tier":"free","photos":0,"messages":0,"trials":3' | head -1)" '"tier":"free","photos":0,"messages":0,"trials":3'
check "essai 1" "$(req "$INSTALL" "$PHOTO")" 200
check "essai 2" "$(req "$INSTALL" "$CHAT")" 200
check "essai 3" "$(req "$INSTALL" "$CHAT")" 200
check "4e demande refusée (402)" "$(req "$INSTALL" "$CHAT")" 402
check "message clair" "$(grep -o 'premium_required' /tmp/lifoody_body)" premium_required
check "calcul automatique refusé en gratuit" "$(req "$(printf '%032x' 12345)" '{"task":"auto","request":{"contents":[{"role":"user","parts":[{"text":"pas"}]}]}}')" 402

echo "Premium : abonnement reconnu, limites du jour"
P="test-premium-$RANDOM$RANDOM"
I2=$(printf '%032x' $RANDOM$RANDOM$RANDOM$RANDOM)
check "statut premium" "$(curl -s "$U/v1/status" -H "X-Lifoody-Install: $I2" -H "X-Lifoody-Purchase: $P" | grep -o '"tier":"premium"')" '"tier":"premium"'
check "photo premium" "$(req "$I2" "$PHOTO" "$P")" 200
check "reste affiché" "$(curl -s -D - -o /dev/null -X POST "$U/v1/generate" -H 'Content-Type: application/json' -H "X-Lifoody-Install: $I2" -H "X-Lifoody-Purchase: $P" --data "$CHAT" | grep -io 'x-lifoody-remaining' | head -1 | tr 'A-Z' 'a-z')" x-lifoody-remaining
codes=""; for i in $(seq 1 8); do codes="$codes $(req "$I2" "$CHAT" "$P")"; done
check "rafale : limite par minute (429)" "$(echo "$codes" | grep -o 429 | head -1)" 429
check "faux abonnement → traité en gratuit" "$(curl -s "$U/v1/status" -H "X-Lifoody-Install: $I2" -H "X-Lifoody-Purchase: faux-jeton-123456" | grep -o '"tier":"free"')" '"tier":"free"'

echo "Requêtes refusées"
check "identifiant d'installation absent" "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$U/v1/generate" -H 'Content-Type: application/json' --data "$CHAT")" 400
check "tâche inconnue" "$(req "$(printf '%032x' 999)" '{"task":"hack","request":{}}')" 400
check "outil interdit (exécution de code)" "$(req "$(printf '%032x' 998)" '{"task":"chat","request":{"contents":[{"role":"user","parts":[{"text":"x"}]}],"tools":[{"code_execution":{}}]}}')" 400
check "faux type d'image" "$(req "$(printf '%032x' 997)" '{"task":"photo","request":{"contents":[{"role":"user","parts":[{"inline_data":{"mime_type":"text/html","data":"PGgxPg=="}}]}]}}')" 400
check "page inconnue" "$(curl -s -o /dev/null -w '%{http_code}' "$U/admin")" 404

echo; echo "$pass réussis, $failn échoués"
[ "$failn" = 0 ]
