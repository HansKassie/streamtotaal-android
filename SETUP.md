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

De git-repo is lokaal al geinitialiseerd met een eerste commit. Online zetten:

```powershell
git remote add origin https://github.com/<jouw-account>/streamfix-android.git
git push -u origin main
```

Maak de repo op GitHub/GitLab als **prive** aan.

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
