#!/usr/bin/env bash
# Proeft publish-release.sh tegen een NEP-R2: een tijdelijke map, met een
# nagebootste curl en wrangler vooraan in PATH. Er gaat niets het internet op:
# de proef stopt als curl of wrangler niet de nepversie is, en APPDATA is leeg,
# zodat ook de echte wrangler.cmd onvindbaar is.
#
#   bash scripts/test-publish-release.sh
#
# Draai dit na elke wijziging aan publish-release.sh; elk geval moet OK zijn.
set -u
BASIS="$(mktemp -d)"
trap 'rm -rf "${BASIS:?}"' EXIT
BRON="$(cd "$(dirname "$0")/.." && pwd)/publish-release.sh"
APK="$BASIS/nep.apk"
head -c 4096 /dev/urandom > "$APK"
HOST="https://updates.smarttv-solutions.xyz"
BIN="$BASIS/bin"
mkdir -p "$BIN"
# In PATH-vorm (/c/...): een C: met dubbele punt breekt PATH, en dan draaien de
# ECHTE curl en wrangler. Zo ging het op 2026-11-18 mis.
BIN_U="$(cygpath -u "$BIN" 2>/dev/null || echo "$BIN")"

# Nep-curl: -o BESTAND -w '%{http_code}' URL. Status per bestand via .status-<naam>.
cat > "$BIN/curl" <<'EOF'
#!/usr/bin/env bash
uit=""; url=""
while [ $# -gt 0 ]; do
  case "$1" in
    -o) uit="$2"; shift 2 ;;
    -w|-X|-H|--data) shift 2 ;;
    http*) url="$1"; shift ;;
    *) shift ;;
  esac
done
case "$url" in https://api.cloudflare.com/*) echo '{"success":false}'; exit 22 ;; esac
echo "$url" >> "$NEP_R2/.verzoeken"
naam="${url##*/}"; naam="${naam%%\?*}"
if [ -f "$NEP_R2/.status-$naam" ]; then code="$(cat "$NEP_R2/.status-$naam")"
elif [ -f "$NEP_R2/$naam" ]; then code=200
else code=404; fi
if [ "$code" = "000" ]; then printf '000'; exit 7; fi
[ "$code" = "200" ] && [ -n "$uit" ] && cp "$NEP_R2/$naam" "$uit"
[ "$code" != "200" ] && [ -n "$uit" ] && printf 'fout' > "$uit"
printf '%s' "$code"
EOF
# Nep-wrangler: r2 object put BUCKET/NAAM --file=F ...; mislukt als .faal-<naam> bestaat.
cat > "$BIN/wrangler" <<'EOF'
#!/usr/bin/env bash
doel="$4"; naam="${doel##*/}"; bestand=""
for a in "$@"; do case "$a" in --file=*) bestand="${a#--file=}" ;; esac; done
echo "upload $naam" >> "$NEP_R2/.uploads"
[ -f "$NEP_R2/.faal-$naam" ] && { echo "nep: upload mislukt" >&2; exit 1; }
cp "$bestand" "$NEP_R2/$naam"
EOF
chmod +x "$BIN/curl" "$BIN/wrangler"

# Harde controle: anders geen enkele proef.
for t in curl wrangler; do
  gevonden="$(PATH="$BIN_U:$PATH" command -v $t)"
  [ "$gevonden" = "$BIN_U/$t" ] || { echo "AFGEBROKEN: $t is niet de nepversie ($gevonden)"; exit 1; }
done
echo "nepversies van curl en wrangler gevonden; geen netwerk"

sha() { sha256sum "$1" | cut -d' ' -f1; }
SHA="$(sha "$APK")"

manifest() {  # code naam notes -> stdout
cat <<EOF
{
    "versionCode": $1,
    "versionName": "$2",
    "apkUrl": "$HOST/zelviro-player-$2.apk",
    "sha256": "${4:-$SHA}",
    "releaseNotes": "$3",
    "minSupportedVersionCode": 1,
    "forceUpdate": false
}
EOF
}

# Nieuwe proef: lokale map met script en release 1.0.5, nep-R2 met live 1.0.4.
nieuw() {
  rm -rf "${BASIS:?}/werk" "${BASIS:?}/r2"; mkdir -p "$BASIS/werk" "$BASIS/r2"
  cp "$BRON" "$BASIS/werk/"
  cp "$APK" "$BASIS/werk/zelviro-player-1.0.5.apk"; cp "$APK" "$BASIS/werk/zelviro-player.apk"
  manifest 6 1.0.5 "Nieuwe versie" > "$BASIS/werk/zelviro.json"
  manifest 5 1.0.4 "Vorige versie" > "$BASIS/r2/zelviro.json"
  cp "$APK" "$BASIS/r2/zelviro-player-1.0.4.apk"; cp "$APK" "$BASIS/r2/zelviro-player.apk"
}

draai() {  # opdracht -> uitvoer in $UIT, exitcode in $RC
  UIT="$(cd "$BASIS/werk" && PATH="$BIN_U:$PATH" APPDATA=/nonexistent NEP_R2="$BASIS/r2" LOCALAPPDATA=/nonexistent ANDROID_HOME=/nonexistent \
        CF_ZONE_ID= CF_PURGE_TOKEN= bash ./publish-release.sh "$@" 2>&1)"; RC=$?
}

FOUT=0
check() {  # naam voorwaarde-bool
  if [ "$2" = "ja" ]; then echo "OK  $1"; else FOUT=1; echo "!!  $1"; printf '%s\n' "$UIT" | tail -5 | sed 's/^/      /'; fi
}
heeft() { printf '%s' "$UIT" | grep -q -- "$1" && echo ja || echo nee; }
uploads() { cat "$BASIS/r2/.uploads" 2>/dev/null | tr '\n' ',' ; }

# 1. 503 bij het manifest: stoppen, niet "eerste release".
nieuw; echo 503 > "$BASIS/r2/.status-zelviro.json"; draai controle
check "manifest 503: stopt" "$([ $RC -ne 0 ] && [ "$(heeft 'HTTP 503')" = ja ] && echo ja || echo nee)"
# 2. Geen verbinding: stoppen.
nieuw; echo 000 > "$BASIS/r2/.status-zelviro.json"; draai controle
check "geen verbinding: stopt" "$([ $RC -ne 0 ] && [ "$(heeft 'In orde')" = nee ] && echo ja || echo nee)"
# 3. 503 bij de versie-APK: stoppen.
nieuw; echo 503 > "$BASIS/r2/.status-zelviro-player-1.0.5.apk"; draai controle
check "versie-APK 503: stopt" "$([ $RC -ne 0 ] && [ "$(heeft 'HTTP 503')" = ja ] && echo ja || echo nee)"
# 4. Echt nog niets live (404): eerste release mag.
nieuw; rm "$BASIS/r2/zelviro.json"; draai controle
check "manifest 404: eerste release mag" "$([ $RC -eq 0 ] && [ "$(heeft 'eerste release')" = ja ] && echo ja || echo nee)"

# 5. Gewone publicatie 1.0.5: alle drie geupload, in de goede volgorde; exit 3 = purge met de hand.
nieuw; draai
check "publicatie: volgorde versie-APK, manifest, vaste APK" "$([ "$(uploads)" = 'upload zelviro-player-1.0.5.apk,upload zelviro.json,upload zelviro-player.apk,' ] && echo ja || echo nee)"
check "cache: vooraf alleen langs de cache, na upload het echte adres" "$(grep "zelviro-player-1.0.5.apk" "$BASIS/r2/.verzoeken" | head -1 | grep -q "?controle=" && grep -qx "https://updates.smarttv-solutions.xyz/zelviro-player-1.0.5.apk" "$BASIS/r2/.verzoeken" && echo ja || echo nee)"
check "publicatie: controle na afloop in orde, purge open (exit 3)" "$([ $RC -eq 3 ] && [ "$(heeft 'apps krijgen 1.0.5')" = ja ] && echo ja || echo nee)"

# 6. Vaste APK mislukt na het manifest; daarna opnieuw: hervatten.
nieuw; touch "$BASIS/r2/.faal-zelviro-player.apk"; draai
check "vaste APK faalt: script stopt met fout" "$([ $RC -ne 0 ] && [ $RC -ne 3 ] && echo ja || echo nee)"
rm "$BASIS/r2/.faal-zelviro-player.apk"; rm -f "$BASIS/r2/.uploads"; draai
check "hervatten: alleen de vaste APK opnieuw" "$([ "$(uploads)" = 'upload zelviro-player.apk,' ] && [ "$(heeft 'staat al live')" = ja ] && echo ja || echo nee)"
check "hervatten: controle na afloop in orde" "$([ $RC -eq 3 ] && [ "$(heeft 'apps krijgen 1.0.5')" = ja ] && echo ja || echo nee)"

# 7. Manifest live, versie-APK weg: hervatten zet hem als eerste terug.
nieuw; draai; rm "$BASIS/r2/zelviro-player-1.0.5.apk" "$BASIS/r2/.uploads"; draai
check "manifest live zonder APK: APK eerst teruggezet" "$([ "$(uploads)" = 'upload zelviro-player-1.0.5.apk,upload zelviro-player.apk,' ] && [ "$(heeft 'apps krijgen 1.0.5')" = ja ] && echo ja || echo nee)"

# 8. Na publicatie het lokale manifest gewijzigd: niet stilletjes hervatten.
nieuw; draai; manifest 6 1.0.5 "Andere tekst" > "$BASIS/werk/zelviro.json"; draai controle
check "gewijzigd manifest bij zelfde versie: geweigerd" "$([ $RC -ne 0 ] && [ "$(heeft 'is gewijzigd sinds')" = ja ] && echo ja || echo nee)"

# 9. Zelfde versie live met een andere APK: geweigerd.
nieuw; manifest 6 1.0.5 "Nieuwe versie" "$(printf 'a%.0s' $(seq 64))" > "$BASIS/r2/zelviro.json"; draai controle
check "zelfde versie, andere APK live: geweigerd" "$([ $RC -ne 0 ] && [ "$(heeft 'met een andere APK')" = ja ] && echo ja || echo nee)"

# 10. Lagere versie dan live: geweigerd.
nieuw; manifest 7 1.0.6 "Nog nieuwer" > "$BASIS/r2/zelviro.json"; draai controle
check "lagere versie dan live: geweigerd" "$([ $RC -ne 0 ] && [ "$(heeft 'Verhoog versionCode')" = ja ] && echo ja || echo nee)"


if [ "$FOUT" = "1" ]; then echo "NIET IN ORDE"; exit 1; fi
echo "Alle gevallen in orde."
