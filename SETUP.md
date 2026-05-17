# StreamFix Android - Fase 0 setup

Dit project is de Android IPTV-app uit de StreamFix-briefing. De code-structuur is
gescaffold. Hieronder de stappen die jij eenmalig handmatig moet doen (deze vereisen
Android Studio, een fysiek toestel of accounts en kunnen niet vanuit code).

## 1. Android Studio en project openen

1. Installeer de laatste stabiele Android Studio.
2. Open de map `Streamfix-Android` (Open, niet importeren).
3. De Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) is al
   gecommit en vastgepind op Gradle 8.11.1, dus `./gradlew` werkt direct
   vanuit de repo (ook in CI). Eerste sync in Android Studio:
   - maakt `local.properties` aan met je Android SDK-pad;
   - downloadt alle libraries.
4. Installeer via de SDK Manager: Android SDK Platform 35 en build-tools.

## 2. Release-keystore aanmaken

De keystore wordt NIET in git bewaard (zie `.gitignore`). Maak hem zo aan:

```powershell
keytool -genkeypair -v `
  -keystore streamfix-release.jks `
  -keyalg RSA -keysize 2048 -validity 9125 `
  -alias streamfix
```

Maak daarna een `keystore.properties` in de projectroot (wordt automatisch
opgepikt door `app/build.gradle.kts`):

```properties
storeFile=../streamfix-release.jks
storePassword=JOUW_STORE_WACHTWOORD
keyAlias=streamfix
keyPassword=JOUW_KEY_WACHTWOORD
```

Belangrijk: bewaar de keystore en wachtwoorden op minimaal twee veilige
locaties. Zonder deze keystore kun je later geen updates uitbrengen die op
dezelfde app installeren.

## 3. Sentry koppelen (pas in Fase 6)

Dit criterium is bewust geparkeerd tot het begin van Fase 6 (zie
"Acceptatiecriteria Fase 0"). De oude testcrash-knop is bij de Fase 1
-herschrijf uit de UI verwijderd, dus verifieer met een bewuste testfout.

1. Maak een gratis account op sentry.io en een nieuw Android-project.
2. Kopieer de DSN.
3. Vul de DSN in `app/src/main/java/nl/streamfix/StreamFixApplication.kt`
   bij de constante `SENTRY_DSN`.
4. Forceer eenmalig een testfout, bijvoorbeeld door tijdelijk
   `throw RuntimeException("Sentry-test")` in `MainActivity.onCreate` te zetten
   (of `Sentry.captureException(...)` aan te roepen), en draai een debug-build.
5. Controleer dat de fout binnenkomt in het Sentry-dashboard en haal de
   tijdelijke testfout daarna weg.

## 4. APK op een toestel

1. Sluit een Android 7.0+ toestel aan met USB-debugging, of gebruik een emulator.
2. Run-configuratie `app`, druk Run (debug-variant).
3. Voor een gesigneerde release-APK: `Build > Generate Signed Bundle / APK`,
   of `./gradlew assembleRelease` (werkt zodra `keystore.properties` bestaat).

## Acceptatiecriteria Fase 0

- [ ] Repository staat online (prive GitHub of GitLab) - zie sectie 5
- [ ] Release-APK is gesigneerd en installeerbaar op Android 7.0+
- [ ] App opent met splash screen en toont het StreamFix-logo
- [ ] ~~Een testcrash verschijnt in het Sentry-dashboard~~ **GEPARKEERD**

> Sentry-criterium is bewust uitgesteld. Reden: tijdens ontwikkeling (Fase 1-5)
> volstaat Logcat. Sentry is pas nodig vlak voor de beta-uitrol, want dan
> draaien onbekende klanttoestellen de app zonder dat je erbij zit.
> Actie: DSN invullen en dit criterium aftekenen aan het begin van Fase 6.
> De code-hook staat klaar (Sentry uit zolang `SENTRY_DSN` leeg is).

## 5. Repository online zetten

De git-repo is lokaal al geinitialiseerd. Online zetten:

```powershell
git remote add origin https://github.com/HansKassie/streamtotaal-android.git
git push -u origin main
```

Let op: voor het in-app updatemechanisme (raw `version.json` + release-
assets anoniem ophalen) moet deze repo **publiek** zijn.

## Wat is al geregeld in code

- Modern Gradle-project (Kotlin DSL, version catalog), package `nl.streamfix`,
  applicationId `nl.streamfix.app`, minSdk 24, targetSdk 35.
- Alle libraries uit de briefing zijn als dependency opgenomen (Compose, Media3,
  Hilt, Room, Retrofit/OkHttp/kotlinx.serialization, Coil, Sentry,
  EncryptedSharedPreferences, WorkManager).
- Hilt is geactiveerd (`StreamFixApplication` met `@HiltAndroidApp`).
- Splash screen via `androidx.core:core-splashscreen` met StreamFix-logo.
- Material 3 donker thema met merkkleuren (`ui/theme/`).
- Adaptive launcher-icon.
- Package-skelet conform de briefing (ui, domain, data/remote, data/local,
  data/repository, player, di, util).
- Debug- en release-buildvariants; release met minify en signing-hook.

## Providerlijst beheren

De klant kiest bij inloggen uit een vaste providerlijst (geen vrije
server-URL meer). De ingebouwde lijst staat in
`data/repository/ProviderRepositoryImpl.kt` (`BUNDLED`).

Wil je providers wijzigen zonder een nieuwe APK uit te rollen: host een
JSON-bestand met de vorm
`[{"name":"PROMAX","url":"http://..."}, ...]` en zet de URL in de
constante `REMOTE_CATALOG_URL` in datzelfde bestand. Is die leeg of
onbereikbaar, dan gebruikt de app de ingebouwde lijst.

## In-app updates via Cloudflare R2

Distributie loopt via een Cloudflare R2-bucket op een eigen domein:
- App haalt op: `https://updates.smarttv-solutions.xyz/version.json`
  (constante `UPDATE_MANIFEST_URL` in `UpdateRepositoryImpl.kt`).
- `version.json` wijst met `apkUrl` naar
  `https://updates.smarttv-solutions.xyz/streamtotaal.apk`.

Eenmalig (Cloudflare-dashboard):
1. R2 > Create bucket, bijv. `streamtotaal-updates`.
2. Bucket > Settings > Public access > Custom Domains > Connect Domain:
   `updates.smarttv-solutions.xyz` (Cloudflare regelt DNS + TLS).
3. Keystore aanmaken en release signen (sectie 2).

Per nieuwe versie uitrollen:
1. Verhoog `versionCode` (en `versionName`) in `app/build.gradle.kts`.
2. Bouw de gesigneerde release-APK, hernoem naar `streamtotaal.apk`.
3. Upload in de R2-bucket (Objects > Upload) `streamtotaal.apk` en het
   bijgewerkte `version.json` (overschrijf de bestaande objecten;
   zelfde `versionCode`/`versionName` als de APK, nieuwe `releaseNotes`).
4. Cloudflare-cache kan kort blijven hangen; eventueel die twee objecten
   purgen via Caching > Configuration.

Migratie vanaf GitHub: reeds geinstalleerde apps (<= 1.0.5) pollen nog de
oude GitHub-`version.json`. Houd die daarom in stand en laat hem naar de
nieuwe versie wijzen totdat vrijwel iedereen op een build met de
Cloudflare-URL zit; daarna kan het GitHub-kanaal vervallen.

De app vergelijkt bij opstart `version.json`-`versionCode` met de
geinstalleerde versie; hoger = updatedialog. `forceUpdate:true` of een
versie onder `minSupportedVersionCode` maakt de update verplicht.

De klant moet bij de eerste update eenmalig "installeren uit onbekende
bronnen" toestaan (systeemprompt; `REQUEST_INSTALL_PACKAGES` staat in de
manifest). Zonder consistente keystore-signing installeert een update
niet over een bestaande app heen.
