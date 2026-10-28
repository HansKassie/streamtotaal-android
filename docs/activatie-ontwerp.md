# Activatie van StreamTotaal, ontwerp

Status: ontwerp, nog niets gebouwd. Termijnen met **(voorstel)** staan nog open.

Raakt twee projecten: deze app en het portaal in `D:\Projecten\TicketSysteem`
met de Supabase-database "StreamTotaal Tickets".

## 1. Besluiten

**Wie mag de app gebruiken, bepaalt een handmatig vinkje in het portaal.**
Standaard uit. Betalingen (9,95 eenmalig voor nieuwe gebruikers) regelt de
beheerder zelf; huidige gebruikers krijgen het gratis.

**Dat vinkje wordt een eigen kolom.** Niet `customer_access.active`, want die
betekent al iets anders, en niet afgeleid van `customer_credentials.expires_at`.
Anders verliest iemand zijn betaalde app-toegang zodra zijn abonnement afloopt,
en dat is een andere afspraak.

**Geen e-mailinlog.** Van 1226 klanten hebben er 73 een portaalaccount. De
sleutel in de database is `phone_e164`. Een activatie op e-mail zou 94 procent
van de klanten buitensluiten of ze eerst laten registreren voor iets wat ze al
hebben.

**Herkennen gebeurt op de gegevens die de app al heeft:** netwerk plus
Xtream-gebruikersnaam. De netwerkcodes in de database zijn A, B, C en D, exact
de letters die de app al kent in `NETWORK_NAME_BY_HOST`.

**Herkennen is niet hetzelfde als bewijzen.** Een gebruikersnaam zegt alleen wie
iemand beweert te zijn. Daarom een uitdaging-en-antwoord over het
Xtream-wachtwoord. Dat wachtwoord verlaat het toestel niet en wordt nergens
nieuw opgeslagen; het portaal heeft het al in `password_enc`.

**Eén apparaat per klant**, serverzijdig vastgelegd. Een tweede apparaat vereist
een handmatige vervanging.

**Serverafhankelijk mag hier.** Eerder stelde ik offline ondertekende licenties
voor, zodat toegang jouw infrastructuur overleeft. Dat argument vervalt: deze
gebruikers zijn IPTV-klanten, en stopt de dienst, dan is de app toch nutteloos.

### Wat niet in versie 1 komt

**Geen normalisatie van hoofdletters.** Zonder bewezen gedrag per provider
kunnen twee verschillende gebruikersnamen samenvallen, en dat merk je pas als
het misgaat. Exact vergelijken, met de koppelcode als terugval.

**Geen PAKE.** Buiten proportie voor dit doel. Zie aanvaarde risico's.

## 2. Datamodel

Alles is toevoegend. Bestaande kolommen, sleutels en versleuteling blijven
ongemoeid.

### `customer_access`, kolommen erbij

| kolom | type | betekenis |
| --- | --- | --- |
| `customer_uid` | `uuid not null unique default gen_random_uuid()` | vaste klantidentiteit |
| `streamtotaal_toegestaan` | `boolean not null default false` | het vinkje |
| `streamtotaal_toegestaan_op` | `timestamptz` | wanneer |
| `streamtotaal_toegestaan_door` | `uuid` | welke beheerder |

`customer_uid` bestaat omdat `phone_e164` de primaire sleutel is en een
telefoonnummer kan wijzigen. Apparaatkoppelingen verwijzen naar de uid, niet
naar het nummer.

De bestaande telefoonsleutels en foreign keys blijven ongewijzigd. Het is
verleidelijk om er `on update cascade` op te zetten, maar dat zou schade
aanrichten: de credentials zijn via AAD cryptografisch gebonden aan
`phone|netwerk`, en `username_hash` net zo. Cascade werkt wel de verwijzingen
bij maar hercodeert niets en herberekent geen hashes, dus dan slaagt een
nummerwijziging in de database terwijl de credentials daarna onleesbaar zijn.
Een foutmelding is beter dan dat.

Eis bij een nummerwijziging: dezelfde `customer_uid` blijft behouden.
Verwijderen en opnieuw aanmaken is daarvoor geen aanvaardbare werkwijze, want
dan is de klantidentiteit en daarmee de apparaatkoppeling alsnog weg. Zie 8
voor de aparte klus die daarbij hoort.

### `app_devices`, nieuw

| kolom | type | betekenis |
| --- | --- | --- |
| `id` | `uuid pk` | |
| `customer_uid` | `uuid not null` → `customer_access.customer_uid` | de klant |
| `app_id_hash` | `text not null` | HMAC van het App-ID, niet de kale waarde |
| `device_token_hash` | `text not null` | alleen de hash, nooit de token zelf |
| `network_code` | `text` | ter informatie |
| `bound_at`, `last_seen_at`, `revoked_at` | `timestamptz` | |
| `note` | `text` | voor handmatig herstel |

Twee gedeeltelijke unieke indexen, beide alleen op rijen met
`revoked_at is null`: één op `customer_uid` (één actief apparaat per klant) en
één op `app_id_hash` (één apparaat dient niet twee klanten). De eerste
koppeling gebeurt met `insert ... on conflict do nothing` tegen die index,
zodat twee gelijktijdige pogingen niet allebei slagen.

### `app_pairing_requests`, nieuw

| kolom | betekenis |
| --- | --- |
| `code` | de korte code die de klant doorgeeft, voor de beheerder |
| `verzoekgeheim_hash` | hash van een lang willekeurig geheim, voor het toestel |
| `app_id_hash`, `network_code`, `opgegeven_gebruikersnaam` | context voor het bevestigingsscherm |
| `created_at`, `expires_at`, `used_at`, `bound_device_id` | |

Twee verschillende dingen, bewust gescheiden. De korte **code** is voor een
mens en gaat via WhatsApp of telefoon; die geeft op zichzelf geen toegang. Het
**verzoekgeheim** blijft in het toestel en komt nooit op het scherm. Alleen de
app die het verzoek aanmaakte kan daarmee na goedkeuring zijn token ophalen.
Wie alleen de code of het App-ID kent, krijgt niets. De server bewaart van dat
geheim alleen de hash.

Geldigheid **15 minuten (voorstel)**, en het verzoek verloopt ook als de
beheerder niets doet. Alfabet voor de code zonder `0`/`O` en zonder `1`/`I`,
want hij wordt overgetypt uit een bericht.

### Pogingen en snelheidsbegrenzing

Eén tabel die elke activatiepoging vastlegt: netwerk, opgegeven gebruikersnaam,
uitkomst, tijdstip, IP-hash.

Daarbij één onderscheid dat er echt toe doet: **een poging zonder geslaagd
wachtwoordbewijs zegt niets over wie die klant is.** Iemand kan andermans
gebruikersnaam opgeven. Alleen een poging mét geslaagd bewijs telt als
aanwijzing dat die klant de app gebruikt, en alleen die mag aanleiding zijn om
iemand gratis toestemming te geven. Ook dan blijft het aanvinken handwerk.

Geen wachtwoorden, geen tokens, geen complete uitdaging-antwoordparen in deze
logging. Bewaartermijn **90 dagen (voorstel)** met automatische opschoning,
alleen zichtbaar voor beheerders.

Het bestaande patroon van `registreer_onbekende_login` met
`unknown_login_attempts` is hiervoor het voorbeeld.

## 3. Automatische route

Het eindpunt draait serverzijdig in het portaal met `SUPABASE_SECRET_KEY`, via
de bestaande helper in `src/lib/supabase/admin.ts`. Die sleutel komt nooit in de
APK. RLS blijft aan op alle tabellen, maar beschermt dit eindpunt niet: de
controles horen in het eindpunt zelf.

1. De app leidt de netwerkcode af uit het serveradres en stuurt netwerk,
   gebruikersnaam en het **kale App-ID** over TLS. De app leidt zelf niets af.
   De server maakt daar `HMAC(serversleutel, "appid|" + app_id)` van en bewaart
   alleen dat. Eén afleiding, op één plek, met één sleutel.
2. De server zoekt binnen dat netwerk. `username_hash` is een HMAC met
   serversleutel over `phone|netwerk|gebruikersnaam`
   (`credentials-crypto.ts`), dus de app kan hem niet zelf maken en de server
   kan niet op de hash zoeken. In plaats daarvan berekent de server die HMAC
   voor elk telefoonnummer in dat netwerk en vergelijkt. Grootste netwerk is A
   met 460 rijen, dus hoogstens 460 HMAC-berekeningen per poging. Geen nieuwe
   kolom, geen hercodering, niets ontcijferd.
3. De server geeft altijd een eenmalige uitdaging terug, ongeacht de uitkomst.
   32 willekeurige bytes, serverzijdig opgeslagen met de App-ID-hash, het
   netwerk en de opgegeven gebruikersnaam erbij. Eenmalig bruikbaar, verwijderd
   bij gebruik, geldig **2 minuten (voorstel)**.
4. De app antwoordt met een HMAC over het Xtream-wachtwoord. Zie "Het
   wachtwoordbewijs" hieronder: het bewijs dekt niet alleen de uitdaging maar
   ook toestel, netwerk en gebruikersnaam.
5. Koppelen gebeurt alleen als dit **allemaal** waar is: precies één treffer, het
   wachtwoordbewijs klopt, `streamtotaal_toegestaan` staat aan, en er is geen
   andere actieve koppeling. Anders de uniforme afwijzing.
6. Bij succes krijgt het apparaat een token met hoge entropie. De server bewaart
   alleen de hash. De app bewaart hem in de bestaande versleutelde opslag.
7. Latere controles gaan met die token, niet opnieuw met het wachtwoord.

### Het wachtwoordbewijs

Het bewijs is `HMAC-SHA256(wachtwoord, bericht)`. Dat bericht dekt uitdaging,
toestel, netwerk én gebruikersnaam, zodat het niet voor een andere koppeling
hergebruikt kan worden. Zou het alleen de uitdaging dekken, dan is de
uitwisseling wel vers maar nergens aan gebonden.

Velden achter elkaar plakken is daarvoor niet goed genoeg: zonder scheiding
levert `"ab" + "c"` hetzelfde op als `"a" + "bc"`, en dan verschuift betekenis
tussen velden. Dus een vaste codering met een protocolversie ervoor:

```
"STA1" || len(app_id)     || app_id
       || len(netwerk)    || netwerk
       || len(gebruiker)  || gebruikersnaam
       || len(uitdaging)  || uitdaging
```

met elke lengte als 4 bytes big-endian en elk veld als UTF-8. De versie
vooraan maakt een latere wijziging mogelijk zonder dat oude en nieuwe bewijzen
elkaar kunnen verwarren.

De server bouwt exact hetzelfde bericht op uit wat hij zelf heeft opgeslagen,
en niet uit wat de app meestuurt. Anders kiest de aanvaller de inhoud.

De apparaattoken is 32 willekeurige bytes; de server bewaart alleen een hash.

### Uniforme afwijzing

Elke uitkomst behalve geslaagd geeft dezelfde melding en dezelfde vervolgroute.
Nul treffers, meerdere treffers, vinkje uit en verkeerd wachtwoord zijn voor de
klant niet te onderscheiden. Anders verklapt het eindpunt of een gebruikersnaam
klant is.

Ook de duur moet zo gelijk mogelijk zijn. Dat de hele netwerkscan altijd draait
scheelt veel, en het weglaten van het ontcijferen scheelt de rest, maar
tijdsonafhankelijk is dit niet. Bij nul treffers hoort een nepverificatie te
draaien zodat die tak niet merkbaar korter is.

## 4. Handmatige terugval: koppelcode

Lukt de automatische route niet, dan toont de app:

```
Apparaat koppelen
Geef code ABCD-1234 door aan de klantenservice om dit apparaat te activeren.
```

De klant gebruikt het bestaande contactkanaal, zodat de beheerder weet bij wie
het hoort. Die opent de klant in het beheer en voert de code in.

Het beheerscherm toont vóór het bevestigen waar de code bij hoort: netwerk,
tijdstip en de opgegeven gebruikersnaam. Codes zijn kort en worden overgetypt;
zonder die controle kan een typefout op een andere lopende code landen.

Is er al een apparaat gekoppeld, dan is er geen stille overschrijving. De
beheerder kiest expliciet "apparaat vervangen".

**Hoe de token bij het juiste toestel komt.** Niet via de code. De app heeft
bij het aanmaken van het verzoek een lang willekeurig geheim bewaard en vraagt
daarmee periodiek of er al goedgekeurd is. Pas na goedkeuring krijgt hij zijn
token, en alleen hij, want alleen hij kent dat geheim. Onderschept iemand de
code uit een appje, dan heeft hij daar niets aan.

Dat navragen heeft een eigen begrenzing, zodat het geen manier wordt om te
peilen. Het verzoek verloopt hoe dan ook na de geldigheidsduur, ook als de
beheerder niets doet; daarna begint de klant opnieuw met een nieuwe code.

## 5. Herstel en intrekken

**Herinstallatie op hetzelfde toestel.** Het App-ID blijft doorgaans gelijk, de
token niet, want die staat in de app-opslag. Herkenning op App-ID alleen geeft
nooit recht op een nieuwe token: een App-ID is geen geheim. De app doorloopt dus
opnieuw de volledige route inclusief wachtwoordbewijs. Slaagt dat en hoort de
bestaande koppeling bij dezelfde App-ID-hash, dan volgt een nieuwe token zonder
tussenkomst van de beheerder.

**Ander toestel of veranderd App-ID.** `ANDROID_ID` blijft meestal gelijk bij
herinstallatie onder dezelfde gebruiker en ondertekening, maar daar zijn
uitzonderingen op; dit is geen garantie en wordt niet als zodanig beloofd. Een
veranderd App-ID loopt via "apparaat vervangen".

**Vinkje uit.** Bij de volgende controle een duidelijke melding, geen zwart
beeld midden in een uitzending.

**Server onbereikbaar is iets anders dan geweigerd.** Een expliciet "geen
toegang" werkt meteen. Bij een storing blijft een eerder goedgekeurd apparaat
doorwerken gedurende **14 dagen (voorstel)**, waarna het alsnog dichtgaat.

## 6. Uitrol

De enige plek waar klanten verloren kunnen gaan. Daarom in stappen, en
handhaven pas als het overgrote deel al gekoppeld is.

1. **Datamodel erbij.** Kolommen en tabellen, vinkje standaard uit. Verandert
   niets aan wat er draait.
2. **Huidige app-gebruikers aanvinken.** Dat is nu een handvol en de beheerder
   weet wie het zijn.
3. **Release 1: koppelen zonder blokkeren.** De app doorloopt de volledige
   automatische route en koppelt stil, maar blokkeert niemand en toont geen
   koppelcode. De voorwaarden uit 3.5 gelden onverkort, ook hier: "nog niet
   blokkeren" mag geen koppeling opleveren zonder geslaagde authenticatie,
   zonder vinkje, of ten koste van een bestaande koppeling.
4. **Kijken wie er binnenkomt.** Elke poging komt in de pogingentabel, ook de
   mislukte. Zo wordt zichtbaar welke klanten de app werkelijk gebruiken, wat nu
   niet bekend is.

   Maar alleen pogingen **met geslaagd wachtwoordbewijs** tellen daarbij als
   aanwijzing. Een kale gebruikersnaam kan iedereen opgeven, dus een
   onbevestigde poging is geen bewijs van wie dan ook en mag nooit de reden
   zijn om iemand toestemming te geven. De rest is enkel materiaal voor de
   snelheidsbegrenzing. Aanvinken blijft handwerk; gekoppeld wordt er bij een
   volgende controle vanzelf.
5. **Release 2: handhaven.** Pas als de staart klein genoeg is. Wie dan nog niet
   gekoppeld is, krijgt de koppelcode.

### Acceptatietests

- Bekende klant, vinkje aan, geen apparaat: koppelt vanzelf, klant merkt niets.
- Bekende klant, vinkje uit: uniforme afwijzing, dezelfde melding als bij een
  onbekende gebruikersnaam.
- Onbekende gebruikersnaam: dezelfde melding, en geen aanwijzing dat de naam
  onbekend is.
- Verkeerd wachtwoordbewijs: dezelfde melding.
- Twee apparaten tegelijk, zelfde klant: precies één wint, de ander krijgt de
  koppelcode.
- Tweede apparaat na een geslaagde koppeling: geweigerd tot handmatige
  vervanging.
- Herinstallatie, zelfde App-ID: nieuwe token na geslaagd wachtwoordbewijs,
  zonder tussenkomst.
- App-ID bekend maar wachtwoordbewijs fout: geen token.
- Vinkje uit tijdens kijken: beeld loopt door, melding bij de volgende controle.
- Server onbereikbaar: blijft werken binnen de respijtperiode, daarna dicht.
- Expliciet "geen toegang": werkt meteen, geen respijt.
- Koppelcode verlopen: niet meer bruikbaar.
- Koppelcode van klant X ingevoerd bij klant Y: het bevestigingsscherm laat het
  verschil zien.
- Koppelcode bekend bij een ander toestel, zonder het verzoekgeheim: na
  goedkeuring krijgt dat toestel geen token, het aanvragende wel.
- Wachtwoordbewijs opnieuw aangeboden voor een ander App-ID of een ander
  netwerk: geweigerd.
- Dezelfde uitdaging twee keer gebruikt: de tweede keer geweigerd.
- Poging zonder geslaagd wachtwoordbewijs: verschijnt niet in de lijst met
  aanwijzingen voor wie de app gebruikt.
- Snelheidsbegrenzing: herhaald proberen vanaf hetzelfde IP wordt geremd, zowel
  bij het zoeken als bij het navragen van een koppelverzoek.

## 7. Aanvaarde risico's

**Offline wachtwoordraden.** Wie TLS weet te onderscheppen, kan aan één
uitdaging-antwoordpaar genoeg hebben om buiten de server om te raden, en
Xtream-wachtwoorden zijn vaak kort en door het panel gegenereerd. Eenmalig
koppelen beperkt de blootstelling tot één uitwisseling per apparaat, maar niet
het gevolg. Een PAKE zou dit oplossen en is hier buiten proportie. Aanvaard, met
TLS, eenmalige uitdaging en snelheidsbegrenzing als beperking.

**De controle zit in de app.** Een sideload-APK kan iemand uit elkaar halen en
de controle eruit slopen. Dit is een drempel, geen slot. De echte grip zit in
het abonnement en in het beheer van het updatekanaal.

**Niet tijdsonafhankelijk.** Zie 3. Verkleind, niet weggenomen.

**App-ID niet gegarandeerd stabiel.** Zie 5.

## 8. Open punten

- Alleen sideload. De playstore-flavor krijgt dit niet, met een
  `buildConfigField` net als `HAS_UPDATER`. Eigen betaling naast Play is niet
  categorisch verboden, maar wel een apart traject.
- **Het nummerwijzigingspad, aparte klus.** Versleuteling én `username_hash`
  zijn via AAD gebonden aan `phone|netwerk`, dus een nummerwijziging vraagt
  hercoderen en herberekenen, plus het nalopen van alle verwijzingen. Dat moet
  een eigen onderzoek- en herstelklus worden, met als eis dat de
  `customer_uid` behouden blijft. Geen proef op productie met echte
  klantgegevens.

  Daarbij hoort deze bevinding, en die is nadrukkelijk een **code- en
  schemabevinding en geen bewezen productiefout**: de foreign keys naar
  `customer_access` staan op `on update no action`, terwijl
  `src/app/actions/customer-access.ts` een nummerwijziging uitvoert als een
  `update` van `phone_e164` zelf. Dat wijst erop dat een nummerwijziging bij
  een klant met abonnementen afketst. Niet uitgeprobeerd, want dat is een
  schrijfactie op productie. Hoort bij dezelfde klus, in een gecontroleerde
  reproductie.
- Netwerk E bestaat in de app en heeft nog geen rij in het portaal.
- Wat de app precies toont bij een niet-gekoppeld toestel in release 2:
  helemaal dicht, of beperkt gebruik. Productkeuze, nog niet gemaakt.
