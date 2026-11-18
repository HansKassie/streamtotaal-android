#!/usr/bin/env bash
#
# Publiceert een klaargezette ZelViro Player-release naar R2 en controleert
# daarna wat klanten echt krijgen. De enige Android-app die we uitbrengen
# (com.zelviro.player); de StreamTotaal-uitvoeringen zijn op 2026-11-18
# verwijderd.
#
# Vooraf:
#   ./gradlew prepareZelviroRelease   bouwt en zet klaar:
#       zelviro-player-<versie>.apk   voor de updater in de app
#       zelviro-player.apk            dezelfde bytes, voor website en Downloader
#       zelviro.json                  versie, SHA-256 en apkUrl
#   releaseNotes in zelviro.json invullen
#   wrangler login                    (eenmalig)
#
# Gebruik:
#   ./publish-release.sh controle   alles nakijken, NIETS uploaden (doe dit eerst)
#   ./publish-release.sh            controle, dan uploaden, dan nakijken wat live staat
#   ./publish-release.sh verify     alleen nakijken wat er nu live staat
#
# Volgorde bij uploaden, zodat een klant nooit een manifest ziet waarvan de
# APK er nog niet is:
#   1. zelviro-player-<versie>.apk  (nieuwe naam: geen oude kopie in de cache)
#   2. nakijken dat die APK via het openbare adres de juiste bytes geeft
#   3. zelviro.json                 (vanaf nu zien apps de update)
#   4. zelviro-player.apk           (vaste naam voor de website; moet gepurged
#                                   worden, anders serveert Cloudflare nog even
#                                   de vorige versie. Met cloudflare.properties
#                                   gaat dat vanzelf.)
#
# Mislukt er halverwege iets (vaste APK, purge, controle), draai het script
# dan gewoon opnieuw: het herkent dezelfde release (zelfde versie, APK en
# manifest) en maakt af wat nog ontbreekt. Alleen een 404 betekent "nog niets
# live"; elke andere fout (503, geen verbinding) laat het script stoppen.
#
# Een gepubliceerde versie wordt nooit overschreven: staat
# zelviro-player-<versie>.apk al online met andere bytes, dan stopt het script.

set -euo pipefail

BUCKET="streamtotaal-updates"
HOST="https://updates.smarttv-solutions.xyz"
MANIFEST="zelviro.json"
VASTE_APK="zelviro-player.apk"
APK_TYPE="application/vnd.android.package-archive"
PAKKET="com.zelviro.player"

cd "$(dirname "$0")"

mode="${1:-all}"
if [ $# -gt 1 ]; then
    echo "FOUT: alleen ZelViro wordt nog gepubliceerd; '$2' bestaat niet meer." >&2
    exit 1
fi

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

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

json_value() {
    sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",]*\)\"\{0,1\}.*/\1/p" "$2" | head -1
}

sha_van() { sha256sum "$1" | cut -d' ' -f1; }

host_of() {
    printf '%s' "$1" | sed -e 's|^[a-zA-Z]*://||' -e 's|[/?].*$||' -e 's|:.*$||' \
        | tr '[:upper:]' '[:lower:]'
}

fout() { echo "FOUT: $*" >&2; exit 1; }

# Het openbare adres ophalen zoals een klant dat doet. Geeft de HTTP-status,
# of 000 als er geen antwoord kwam.
haal_op() {
    local code
    code="$(curl -sS -o "$2" -w '%{http_code}' "$1" 2>/dev/null)" || code="000"
    printf '%s' "$code"
}

# Hetzelfde, maar langs de cache heen (een unieke zoekparameter). Voor de
# controles VOOR het uploaden: vraag je het echte adres van een APK op die
# nog niet bestaat, dan onthoudt Cloudflare die 404, en krijgen klanten (en
# de controle na het uploaden) nog even een 404 terwijl het bestand er al
# staat. Zo bleef bij de proef op 2026-11-18 de controle na het uploaden
# steken. Na het uploaden controleren we juist WEL het echte adres.
haal_vers() {
    haal_op "$1?controle=$(date +%s)$RANDOM" "$2"
}

# ---------- controle vooraf: lokaal en tegenover wat er live staat ----------

controle() {
    [ -f "$MANIFEST" ] || fout "$MANIFEST ontbreekt."
    WIL_CODE="$(json_value versionCode "$MANIFEST")"
    WIL_NAAM="$(json_value versionName "$MANIFEST")"
    WIL_SHA="$(json_value sha256 "$MANIFEST")"
    WIL_URL="$(json_value apkUrl "$MANIFEST")"
    NOTES="$(json_value releaseNotes "$MANIFEST")"
    VERSIE_APK="zelviro-player-$WIL_NAAM.apk"

    case "$WIL_CODE" in ''|*[!0-9]*) fout "versionCode in $MANIFEST is geen getal." ;; esac
    [ -n "$WIL_NAAM" ] || fout "versionName ontbreekt in $MANIFEST."
    [ "$WIL_URL" = "$HOST/$VERSIE_APK" ] || fout "apkUrl in $MANIFEST is '$WIL_URL', verwacht $HOST/$VERSIE_APK. Draai prepareZelviroRelease."
    [ -n "$NOTES" ] || fout "releaseNotes in $MANIFEST is leeg."

    [ -f "$VERSIE_APK" ] || fout "$VERSIE_APK ontbreekt. Draai eerst ./gradlew prepareZelviroRelease."
    [ -f "$VASTE_APK" ] || fout "$VASTE_APK ontbreekt. Draai eerst ./gradlew prepareZelviroRelease."
    [ "$(sha_van "$VERSIE_APK")" = "$WIL_SHA" ] || fout "de SHA-256 in $MANIFEST hoort niet bij $VERSIE_APK. Draai prepareZelviroRelease opnieuw (releasebuilds zijn niet byte-gelijk)."
    [ "$(sha_van "$VASTE_APK")" = "$WIL_SHA" ] || fout "$VASTE_APK is niet dezelfde APK als $VERSIE_APK."

    # Pakket en versie uit de APK zelf, als aapt er is (Android SDK).
    local aapt
    aapt="$(ls -d "${LOCALAPPDATA:-/nonexistent}/Android/Sdk/build-tools/"*/aapt.exe \
        "${ANDROID_HOME:-/nonexistent}/build-tools/"*/aapt 2>/dev/null | tail -1 || true)"
    if [ -n "$aapt" ]; then
        local badging
        badging="$("$aapt" dump badging "$VERSIE_APK" | head -1)"
        case "$badging" in
            *"name='$PAKKET' versionCode='$WIL_CODE' versionName='$WIL_NAAM'"*) ;;
            *) fout "de APK zegt iets anders dan $MANIFEST: $badging" ;;
        esac
    else
        echo "Let op: aapt niet gevonden; pakket en versie in de APK niet nagekeken."
    fi

    # Tegenover wat er live staat. Alleen een 404 betekent "nog niets live";
    # elke andere status (503, 000 bij een netwerkfout, ...) is onbekend, en
    # dan stoppen we liever dan op goed geluk door te gaan.
    local status
    HERVAT=0
    status="$(haal_vers "$HOST/$MANIFEST" "$TMP/live.json")"
    case "$status" in
        200)
            LIVE_CODE="$(json_value versionCode "$TMP/live.json")"
            LIVE_SHA="$(json_value sha256 "$TMP/live.json")"
            LIVE_URL="$(json_value apkUrl "$TMP/live.json")"
            LIVE_NOTES="$(json_value releaseNotes "$TMP/live.json")"
            case "$LIVE_CODE" in ''|*[!0-9]*) fout "het live manifest heeft geen geldige versionCode." ;; esac
            if [ "$WIL_CODE" -lt "$LIVE_CODE" ]; then
                fout "live staat al versiecode $LIVE_CODE; deze release is $WIL_CODE. Verhoog versionCode en versionName in app/build.gradle.kts."
            elif [ "$WIL_CODE" -eq "$LIVE_CODE" ]; then
                # Hervatten: dezelfde release staat al (deels) live, bijvoorbeeld
                # omdat de vaste APK, de purge of de controle mislukte. Alleen als
                # het precies dezelfde release is: zelfde APK, zelfde adres, en
                # het manifest niet meer gewijzigd sinds de upload.
                [ "$LIVE_SHA" = "$WIL_SHA" ] && [ "$LIVE_URL" = "$WIL_URL" ] \
                    || fout "live staat al versiecode $LIVE_CODE met een andere APK. Verhoog versionCode en versionName in app/build.gradle.kts."
                cmp -s "$MANIFEST" "$TMP/live.json" \
                    || fout "$MANIFEST is gewijzigd sinds deze versie live ging. Zet hem terug zoals hij live staat, of breng een nieuwe versie uit."
                HERVAT=1
            else
                [ "$NOTES" != "$LIVE_NOTES" ] || fout "de releaseNotes zijn nog die van de vorige versie. Pas ze aan in $MANIFEST."
            fi
            ;;
        404)
            echo "Let op: $HOST/$MANIFEST bestaat niet (404); dit wordt de eerste release."
            LIVE_CODE=0
            ;;
        *)
            fout "$HOST/$MANIFEST gaf HTTP $status. Onduidelijk wat er live staat; probeer het later opnieuw."
            ;;
    esac

    # Een gepubliceerde versie nooit overschrijven.
    status="$(haal_vers "$HOST/$VERSIE_APK" "$TMP/bestaand.apk")"
    BESTAAT_AL=0
    case "$status" in
        200)
            [ "$(sha_van "$TMP/bestaand.apk")" = "$WIL_SHA" ] || fout "$VERSIE_APK staat al online met andere bytes. Een gepubliceerde versie overschrijven we niet: verhoog de versie."
            BESTAAT_AL=1
            ;;
        404) ;;
        *) fout "$HOST/$VERSIE_APK gaf HTTP $status. Onduidelijk of deze versie al online staat; probeer het later opnieuw." ;;
    esac
    echo "In orde: ZelViro $WIL_NAAM (code $WIL_CODE, live nu ${LIVE_CODE}), sha256 ${WIL_SHA:0:12}..."
    if [ "$HERVAT" = "1" ] && [ "$BESTAAT_AL" = "0" ]; then
        echo "  LET OP: het live manifest wijst al naar $VERSIE_APK, maar die ontbreekt (404)."
        echo "  Apps kunnen deze update nu niet downloaden; het script uploadt hem als eerste."
    elif [ "$HERVAT" = "1" ]; then
        echo "  Deze release staat al live; het script maakt de rest af (vaste APK, purge, controle)."
    elif [ "$BESTAAT_AL" = "1" ]; then
        echo "  $VERSIE_APK staat al online met dezelfde bytes; die upload wordt overgeslagen."
    fi
    return 0
}

# ---------- uploaden ----------

upload() {
    "$WRANGLER" r2 object put "$BUCKET/$1" --file="$1" --content-type="$2" --remote
}

purge() {
    local url="$1"
    if [ -z "${CF_ZONE_ID:-}" ] || [ -z "${CF_PURGE_TOKEN:-}" ]; then
        echo
        echo "Niet gepurged (geen CF_ZONE_ID/CF_PURGE_TOKEN in $SECRETS)."
        echo "  Doe dat in Cloudflare: Caching, Configuration, Purge Cached Content, op URL:"
        echo "    $url"
        echo "  Tot dan krijgt de website mogelijk nog de vorige versie; de updater in"
        echo "  de app gebruikt de versie-APK en merkt daar niets van."
        return 1
    fi
    local payload response
    payload="$(printf '{"files":["%s"]}' "$url")"
    response="$(curl -fsS -X POST \
        "https://api.cloudflare.com/client/v4/zones/${CF_ZONE_ID}/purge_cache" \
        -H "Authorization: Bearer ${CF_PURGE_TOKEN}" \
        -H "Content-Type: application/json" \
        --data "$payload")" || { echo "Purge-aanroep mislukt." >&2; return 1; }
    case "$response" in
        *'"success":true'*) echo "Gepurged: $url"; return 0 ;;
        *) echo "Cloudflare weigerde de purge: $response" >&2; return 1 ;;
    esac
}

publiceer() {
    find_wrangler
    if [ "$BESTAAT_AL" = "0" ]; then
        echo "1. $VERSIE_APK uploaden"
        upload "$VERSIE_APK" "$APK_TYPE"
    fi
    echo "2. $VERSIE_APK via het openbare adres nakijken"
    local status
    status="$(haal_op "$HOST/$VERSIE_APK" "$TMP/check.apk")"
    [ "$status" = "200" ] && [ "$(sha_van "$TMP/check.apk")" = "$WIL_SHA" ] \
        || fout "$HOST/$VERSIE_APK geeft HTTP $status of andere bytes. Het manifest is NIET geupload; klanten merken niets. Purge die URL en draai het script opnieuw."
    if [ "$HERVAT" = "1" ]; then
        echo "3. $MANIFEST staat al zo live; overgeslagen"
    else
        echo "3. $MANIFEST uploaden"
        upload "$MANIFEST" "application/json"
    fi
    echo "4. $VASTE_APK uploaden (website en Downloader)"
    upload "$VASTE_APK" "$APK_TYPE"
    purge "$HOST/$VASTE_APK" || PURGE_OPEN=1
}

# ---------- nakijken wat er live staat ----------

# Doet wat de app straks doet: het live manifest lezen en de APK ophalen via
# de apkUrl daaruit, en de bytes hashen.
verify() {
    echo
    echo "Controle op wat er live staat"
    local status live_code live_naam live_hash live_url
    status="$(haal_op "$HOST/$MANIFEST" "$TMP/live.json")"
    [ "$status" = "200" ] || fout "$HOST/$MANIFEST gaf HTTP $status."
    live_code="$(json_value versionCode "$TMP/live.json")"
    live_naam="$(json_value versionName "$TMP/live.json")"
    live_hash="$(json_value sha256 "$TMP/live.json")"
    live_url="$(json_value apkUrl "$TMP/live.json")"
    echo "  manifest: $live_naam (code $live_code)"

    if [ -n "${WIL_CODE:-}" ]; then
        [ "$live_code" = "$WIL_CODE" ] && [ "$live_hash" = "$WIL_SHA" ] \
            || fout "het live manifest is niet wat je wilde publiceren (live code $live_code, lokaal $WIL_CODE)."
    fi

    # De app weigert een apkUrl die niet via https op het eigen updatedomein staat.
    case "$live_url" in https://*) ;; *) fout "apkUrl is geen https: $live_url" ;; esac
    [ "$(host_of "$live_url")" = "$(host_of "$HOST")" ] || fout "apkUrl wijst naar een andere host: $live_url"

    status="$(haal_op "$live_url" "$TMP/live.apk")"
    [ "$status" = "200" ] || fout "$live_url gaf HTTP $status."
    [ "$(sha_van "$TMP/live.apk")" = "$live_hash" ] || fout "de APK via apkUrl hoort niet bij het manifest. Purge $live_url."
    echo "  APK voor de updater: in orde (${live_hash:0:12}...)"

    status="$(haal_op "$HOST/$VASTE_APK" "$TMP/vast.apk")"
    if [ "$status" = "200" ] && [ "$(sha_van "$TMP/vast.apk")" = "$live_hash" ]; then
        echo "  APK voor website en Downloader: in orde"
    else
        echo "  LET OP: $HOST/$VASTE_APK is (nog) niet dezelfde versie. Purge die URL in Cloudflare;"
        echo "  de updater in de app heeft hier geen last van."
        VAST_OPEN=1
    fi

    echo
    echo "In orde: apps krijgen $live_naam en de sha256-controle slaagt."
}

PURGE_OPEN=0
VAST_OPEN=0
case "$mode" in
    controle)
        controle
        echo "Niets geupload (controle)."
        ;;
    all)
        controle
        publiceer
        verify
        ;;
    verify)
        verify
        ;;
    eigen|reseller|apk|manifest|purge)
        fout "'$mode' bestaat niet meer: alleen ZelViro wordt nog gepubliceerd. Gebruik controle, verify of geen argument."
        ;;
    *)
        fout "onbekende opdracht '$mode'. Gebruik controle, verify of geen argument."
        ;;
esac

if [ "$PURGE_OPEN" = "1" ] || [ "$VAST_OPEN" = "1" ]; then
    exit 3
fi
