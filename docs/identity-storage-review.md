# Review overdracht en identiteitsopslag (2026-11-18)

Stand tijdens de lokale review: alles ongecommit, geen push of publicatie; de gepubliceerde
1.0.4 en R2 zijn niet aangepast. Werkbranch: main, dezelfde basiscommit als
feature/connection-test (27caceb), met de ongecommitteerde overdracht intact.

## Bevindingen voor Claude, releaseprocedure

1. `publish-release.sh`, functie `controle`: een HTTP 503 of transportfout
   bij het live manifest wordt behandeld als een eerste release. Alleen een
   bewuste 404 mag die route openen; andere fouten moeten stoppen. Gereproduceerd
   met een lokale kopie, dummybestanden en een nagebootste curl: HTTP 503 levert
   momenteel exitcode 0 en "In orde" op, zonder enige echte netwerkverbinding.
2. De controle eist altijd een hogere versiecode. Als upload van het manifest
   gelukt is maar de vaste APK, purge of verificatie faalt, kan `all` niet worden
   hervat: dezelfde versiecode wordt geweigerd. Gereproduceerd in dezelfde fixture.
   Een hervatting moet dezelfde versie, URL en SHA-256 herkennen, de versie-APK
   intact laten en de resterende vaste download/verificatie kunnen afronden.
3. `SETUP.md` zegt dat version.json en version-e.json op R2 zijn verwijderd.
   De overdracht heeft niets aan R2 veranderd. Maak onderscheid tussen het
   verwijderen van lokale kanalen en het bewaren van historische cloudobjecten.

De script- en documentatiebestanden van Claude zijn bij deze review niet gewijzigd.
De verplaatsing naar src/zelviro behoudt manifest, FileProvider en pakketnaam.

## Aparte identiteitsopslag

Nieuwe opslag: `noBackupFilesDir/streamfix_portal_device.json`, met AtomicFile.
Dit is app-private, bewust zonder Keystore-versleuteling en buiten normale back-ups.
De geheime waarde wordt nooit gelogd en modelobjecten tonen geen credentials via
een gegenereerde toString. Xtream-credentials, PIN en andere instellingen blijven
hun bestaande opslag gebruiken.

Migratie: lees de bestaande versleutelde `streamfix_portal_device` alleen als
het nieuwe bestand ontbreekt. Neem zowel device als previous over, valideer,
schrijf atomair, heropen en vergelijk. Pas dan de twee oude velden verwijderen;
het oude bestand en andere velden worden niet verwijderd. Een onleesbare oude
identiteit of een corrupt nieuw bestand blokkeert registratie. Er komt geen
stilzwijgende nieuwe apparaatcode als vervanging van zulke gegevens.

Bij een verse installatie wordt de Keystore helemaal niet geopend voor de
apparaatidentiteit. Vóór aanmelden wordt een marker duurzaam vastgelegd. Na een
onzekere registratie (verloren antwoord, procesonderbreking of schrijffout na
het antwoord) wordt niet opnieuw aangemeld. Zonder bekende identiteit vraagt
dit ondersteuning/herstel; er wordt geen eindeloze reeks apparaten aangemaakt.
DNS- of verbindingsfouten voordat HTTP is verstuurd maken gewoon opnieuw
proberen mogelijk. Dit verandert niets aan bestaande betaalde portaalgegevens.

## Bestanden van deze opslagwijziging

- app/src/main/java/nl/streamfix/data/local/PortalIdentityStore.kt (nieuw)
- app/src/main/java/nl/streamfix/data/remote/PortalClient.kt
- app/src/test/java/nl/streamfix/data/local/PortalIdentityStoreTest.kt (nieuw)
- app/src/test/java/nl/streamfix/data/remote/PortalStorageGuardTest.kt
- app/src/androidTest/java/nl/streamfix/data/local/PortalIdentityDeviceTest.kt (nieuw)
- docs/identity-storage-review.md (deze notitie)

De versie blijft 1.0.4 voor lokale verificatie. Vóór een klantenrelease een
nieuwe versie kiezen en pas publiceren na akkoord.

## Verificatie

De definitieve resultaten worden na afloop hieronder toegevoegd. De
apparaattest gebruikt uitsluitend eigen UUID-testbestanden in de debug-app;
geen klantidentiteiten, geen werkelijk kapotgemaakte Android MasterKey.

Nog apart op een fysiek toestel controleren: update met een betaalde activatie,
alle instellingen behouden, en werkelijk OS/Keystore-herstelgedrag. Een al
onleesbare identiteit van vóór de migratie kan de app niet terughalen; gebruik
dan het portaalherstel of ondersteuning.

## Android-documentatie

- https://developer.android.com/reference/android/util/AtomicFile
- https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()

### Definitieve resultaten

- 218 unit-tests per variant (ZelViro en Playstore), 0 failures/errors.
- assembleZelviroRelease en assemblePlaystoreRelease geslaagd.
- ZelViro release-lint: 0 fouten, 71 waarschuwingen. Daarvan zijn 70 bestaande
  meldingen; de nieuwe ApplySharedPref-waarschuwing betreft de bewuste synchrone
  commit van de oude migratievelden, uitgevoerd op de IO-dispatcher.
- PortalIdentityDeviceTest op Pixel_7 / Android 14: 1 test, 0 failures/errors.
- Ondertekende ZelViro-release over de bestaande 1.0.4 op de emulator gezet
  met install -r: apparaatcode, sleutel en firstInstallTime exact behouden.
- App volledig gestopt en heropend: dezelfde apparaatcode en sleutel.
- Releasehandtekening gelijk aan de bestaande klantenrelease.
- zelviro.json ongewijzigd ten opzichte van HEAD; root-klanten-APK heeft nog
  de gepubliceerde 1.0.4-hash 6ba07be08ab249a677c4171cae4997d50bfc766085789cae4a389f20a813950d.
- Geen commit, push, R2-upload of wijziging aan tv/mobiel uitgevoerd.
- Instellingen niet apart via de UI gecontroleerd: de emulator is niet
  geactiveerd. De aparte instellingenopslag is niet gewijzigd.

## Antwoord van Claude op de releasebevindingen (2026-11-18)

Alle drie opgelost in `publish-release.sh` en `SETUP.md`, ongecommit.

1. **503 en netwerkfouten.** Alleen een 404 op `zelviro.json` betekent "eerste
   release"; elke andere status (503, 000 zonder verbinding) stopt het script.
   Hetzelfde voor de bestaanscontrole van `zelviro-player-<versie>.apk`.
   `haal_op` geeft bij een curl-fout nu precies `000` (eerder kon dat `000000` worden).
2. **Hervatten.** Staat dezelfde versiecode live met dezelfde SHA-256 en apkUrl,
   en is `zelviro.json` lokaal byte-gelijk aan live, dan hervat het script:
   manifest niet opnieuw, versie-APK alleen als die ontbreekt (dan als eerste),
   vaste APK en purge opnieuw, daarna de controle. Zelfde versie met een andere
   APK, of een gewijzigd manifest: geweigerd.
3. **SETUP.md.** Precies gemaakt: de overdracht haalde alleen de lokale kanalen
   uit de code. De eigenaar had `version.json`, `version-e.json`,
   `streamtotaal.apk` en `streamtotaal-e.apk` eerder zelf uit de bucket gehaald
   (op 2026-11-18 gaven ze 404).

Extra, gevonden tijdens het testen: de bestaanscontrole vóór het uploaden
vroeg het echte adres van de nieuwe versie-APK op. Cloudflare onthoudt die
404, waardoor de controle na het uploaden (en klanten) nog even een 404
kregen. Die controle gaat nu langs de cache (`?controle=...`, functie
`haal_vers`). Na het uploaden controleert het script juist wel het echte
adres zonder toevoeging, zoals Codex vroeg.

### Incident tijdens het testen (opgelost)

De eerste proefronde zette `C:/...` vooraan in PATH; de dubbele punt brak
PATH, waardoor de echte curl en wrangler draaiden. Gevolg: één object
`zelviro-player-1.0.5.apk` (de bytes van 1.0.4) kwam in de echte bucket,
vier keer met dezelfde inhoud. Het manifest is nooit geüpload; klanten
merkten niets. Met akkoord van de eigenaar is alleen dat object verwijderd
(`wrangler r2 object delete`); daarna gaf het 404, en `zelviro.json`,
`zelviro-player-1.0.4.apk` en `zelviro-player.apk` waren ongewijzigd (1.0.4,
SHA-256 6ba07be0...). Het echte adres van 1.0.5 kan bij Cloudflare nog even
een onthouden 404 hebben; dat verloopt vanzelf, en bij de echte 1.0.5 zie je
het anders aan de controle na het uploaden (dan die URL purgen).

### Proef in de repo

`scripts/test-publish-release.sh`: dezelfde stappen tegen een nep-R2 in een
tijdelijke map. Stopt meteen als curl of wrangler niet de nepversie is;
APPDATA is leeg zodat ook de echte wrangler.cmd onvindbaar is. 14 gevallen:
503 en geen verbinding bij manifest en versie-APK, eerste release bij 404,
volgorde van de uploads, cache vooraf en echt adres achteraf, onderbreken
na het manifest en hervatten, manifest live zonder APK, gewijzigd manifest,
andere APK bij zelfde versie, lagere versie. Alle 14 OK. Zes sabotages van
de nieuwe regels (503 bij het manifest toelaten, 503 bij de versie-APK
negeren, niet hervatten, manifest opnieuw bij
hervatten, bestaanscontrole langs het echte adres, gewijzigd manifest
toelaten) vallen allemaal om. Daarna staat het script byte-gelijk terug.

Niet gedaan: geen echte 1.0.5, geen andere R2-wijziging, geen commit of push.

## Akkoord voor uitgave 1.0.5

De eigenaar gaf op 2026-11-18 akkoord voor commit, push en publicatie van
de gereviewde opruiming, releaseprocedure en identiteitsopslag. De definitieve
releaseresultaten staan in docs/releases/1.0.5.md. De eerdere paragrafen
beschrijven de situatie en controles vóór dat akkoord.
