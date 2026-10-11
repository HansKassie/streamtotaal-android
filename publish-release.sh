#!/usr/bin/env bash
#
# Rolt de klaargezette sideload-release uit naar R2 en controleert daarna of
# klanten echt de nieuwe APK krijgen.
#
# Vooraf:
#   ./gradlew prepareSideloadRelease     (schrijft streamtotaal.apk + version.json)
#   wrangler login                       (eenmalig)
#
# Voor het leegmaken van de edge-cache: zet CF_ZONE_ID en CF_PURGE_TOKEN in
# cloudflare.properties naast dit script (buiten git, net als
# keystore.properties). Zie cloudflare.properties.example. Omgevingsvariabelen
# met dezelfde namen gaan voor, handig als je het ooit automatisch draait.
# PURGEN IS VERPLICHT. De edge cachet de APK op die exacte URL, ook al meldt
# een HEAD-verzoek "cf-cache-status: DYNAMIC". Dat is misleidend: een GET op
# dezelfde URL geeft gewoon HIT met de oude bytes. Zonder purge serveert de
# edge de oude APK terwijl version.json al de nieuwe aankondigt, en slaat de
# sha256-controle in de app terecht aan: klanten zien "De update is mislukt".
# Vastgesteld bij 1.0.20 op 2026-10-11.
#
# Gebruik:
#   ./publish-release.sh            alles: APK, purge, manifest, controle
#                                   zonder purgegegevens stopt hij na de APK
#   ./publish-release.sh apk        alleen de APK (plus purge)
#   ./publish-release.sh manifest   alleen version.json (plus controle)
#   ./publish-release.sh verify     alleen controleren wat er live staat

set -euo pipefail

BUCKET="streamtotaal-updates"
HOST="https://updates.smarttv-solutions.xyz"
APK="streamtotaal.apk"
MANIFEST="version.json"
APK_TYPE="application/vnd.android.package-archive"

cd "$(dirname "$0")"

SECRETS="cloudflare.properties"

# Bewust regel voor regel uitlezen en niet sourcen: een bestand met geheimen
# hoort data te zijn, geen code die we uitvoeren.
read_secret() {
    [ -f "$SECRETS" ] || return 0
    sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$SECRETS" | head -1 | sed 's/[[:space:]]*$//'
}

if [ -f "$SECRETS" ] && git ls-files --error-unmatch "$SECRETS" >/dev/null 2>&1; then
    echo "FOUT: $SECRETS staat in git. Haal hem eruit voordat je verder gaat:" >&2
    echo "  git rm --cached $SECRETS" >&2
    exit 1
fi

CF_ZONE_ID="${CF_ZONE_ID:-$(read_secret CF_ZONE_ID)}"
CF_PURGE_TOKEN="${CF_PURGE_TOKEN:-$(read_secret CF_PURGE_TOKEN)}"

# Wrangler staat op Windows in APPDATA/npm en niet altijd op de PATH. Een
# .cmd heeft daar geen uitvoerbit, dus testen op bestaan en niet op -x.
# Alleen nodig om te uploaden; "verify" werkt ook zonder.
find_wrangler() {
    if command -v wrangler >/dev/null 2>&1; then
        WRANGLER="wrangler"
    elif [ -f "${APPDATA:-}/npm/wrangler.cmd" ]; then
        WRANGLER="${APPDATA}/npm/wrangler.cmd"
    else
        echo "FOUT: wrangler niet gevonden. Installeer met: npm install -g wrangler" >&2
        exit 1
    fi
}

mode="${1:-all}"

json_value() {
    sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",]*\)\"\{0,1\}.*/\1/p" "$2" | head -1
}

check_pair() {
    [ -f "$APK" ] || { echo "FOUT: $APK ontbreekt. Draai eerst prepareSideloadRelease." >&2; exit 1; }
    [ -f "$MANIFEST" ] || { echo "FOUT: $MANIFEST ontbreekt." >&2; exit 1; }

    local local_hash manifest_hash
    local_hash="$(sha256sum "$APK" | cut -d' ' -f1)"
    manifest_hash="$(json_value sha256 "$MANIFEST")"

    if [ "$local_hash" != "$manifest_hash" ]; then
        echo "FOUT: de hash in $MANIFEST hoort niet bij $APK." >&2
        echo "  APK:      $local_hash" >&2
        echo "  manifest: $manifest_hash" >&2
        echo "Release-builds zijn niet byte-reproduceerbaar, dus beide bestanden" >&2
        echo "moeten uit dezelfde prepareSideloadRelease-run komen." >&2
        exit 1
    fi
    echo "Paar klopt: versie $(json_value versionName "$MANIFEST") (code $(json_value versionCode "$MANIFEST")), sha256 ${local_hash:0:12}..."
}

purge() {
    local url="$1"
    if [ -z "${CF_ZONE_ID:-}" ] || [ -z "${CF_PURGE_TOKEN:-}" ]; then
        echo
        echo "GESTOPT NA DE APK: geen CF_ZONE_ID/CF_PURGE_TOKEN, dus niet gepurged."
        echo
        echo "  Het manifest is NIET geupload, en dat is met opzet. Zolang dat"
        echo "  nog de vorige versie aankondigt merkt niemand iets. Zou het er"
        echo "  nu al staan, dan kondigt de app een update aan terwijl de edge"
        echo "  de oude APK serveert, en zien klanten "De update is mislukt"."
        echo
        echo "  1. Purge in het Cloudflare-dashboard, Caching, Configuration,"
        echo "     Purge Cached Content, op URL:"
        echo "       $url"
        echo "  2. Draai daarna: ./publish-release.sh manifest"
        echo
        echo "  Of vul cloudflare.properties, dan gaat dit voortaan vanzelf."
        exit 2
    fi
    echo "Purgen: $url"
    curl -fsS -X POST         "https://api.cloudflare.com/client/v4/zones/${CF_ZONE_ID}/purge_cache"         -H "Authorization: Bearer ${CF_PURGE_TOKEN}"         -H "Content-Type: application/json"         --data "{"files":["${url}"]}" >/dev/null
    echo "Purge verstuurd."
}
upload_apk() {
    find_wrangler
    echo "Uploaden: $APK naar $BUCKET"
    "$WRANGLER" r2 object put "$BUCKET/$APK" \
        --file="$APK" --content-type="$APK_TYPE" --remote
    purge "$HOST/$APK"
}

upload_manifest() {
    find_wrangler
    echo "Uploaden: $MANIFEST naar $BUCKET"
    "$WRANGLER" r2 object put "$BUCKET/$MANIFEST" \
        --file="$MANIFEST" --content-type="application/json" --remote
}

verify() {
    echo
    echo "Controle op wat er live staat"
    local tmp live_code live_hash want_hash want_code
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    curl -fsS "$HOST/$MANIFEST" -o "$tmp/version.json"
    live_code="$(json_value versionCode "$tmp/version.json")"
    want_code="$(json_value versionCode "$MANIFEST")"
    want_hash="$(json_value sha256 "$MANIFEST")"

    echo "  manifest live: versionCode $live_code (verwacht $want_code)"
    [ "$live_code" = "$want_code" ] || { echo "FOUT: manifest live komt niet overeen." >&2; return 1; }

    # Niet alleen de headers lezen: de bytes echt ophalen en hashen.
    curl -fsS "$HOST/$APK" -o "$tmp/live.apk"
    live_hash="$(sha256sum "$tmp/live.apk" | cut -d' ' -f1)"
    echo "  APK live:      ${live_hash:0:12}... (verwacht ${want_hash:0:12}...)"
    [ "$live_hash" = "$want_hash" ] || {
        echo "FOUT: de APK die de edge serveert is niet de nieuwe." >&2
        echo "Purge $HOST/$APK en draai './publish-release.sh verify' opnieuw." >&2
        return 1
    }

    echo
    echo "In orde: klanten krijgen $(json_value versionName "$MANIFEST") en de sha256-controle slaagt."
}

case "$mode" in
    all)
        check_pair
        upload_apk
        upload_manifest
        verify
        ;;
    apk)
        check_pair
        upload_apk
        ;;
    manifest)
        check_pair
        upload_manifest
        verify
        ;;
    verify)
        verify
        ;;
    *)
        echo "Onbekende modus: $mode (gebruik: all, apk, manifest of verify)" >&2
        exit 1
        ;;
esac
