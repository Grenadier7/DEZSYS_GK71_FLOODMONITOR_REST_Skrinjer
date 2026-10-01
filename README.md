# DEZSYS GK71 — Hochwasser-Frühwarnsystem (REST)

REST-Schnittstelle eines Hochwasser-Frühwarnsystems mit simulierten Messstationen,
serverseitig berechneten Warnstufen, Content Negotiation zwischen JSON und XML und
einem Webclient zur Visualisierung.

**Technik:** Java 21 · Spring Boot 4.1.1 · Gradle · Jackson 3 (JSON + XML) ·
jQuery 3.7 · springdoc-openapi 3 · JUnit 5

---

## Inhalt

1. [Schnellstart](#1-schnellstart)
2. [Architektur](#2-architektur)
3. [Datenmodell](#3-datenmodell)
4. [Simulationsregeln](#4-simulationsregeln)
5. [Warnstufen](#5-warnstufen)
6. [REST-Schnittstelle](#6-rest-schnittstelle)
7. [JSON und XML](#7-json-und-xml)
8. [Fehlerbehandlung](#8-fehlerbehandlung)
9. [Webclient](#9-webclient)
10. [API-Dokumentation](#10-api-dokumentation)
11. [Automatisierte Tests](#11-automatisierte-tests)
12. [Konfiguration](#12-konfiguration)
13. [Reflexion](#13-reflexion)
14. [Offene Punkte](#14-offene-punkte)

---

## 1. Schnellstart

Voraussetzung: JDK 21 oder neuer. Gradle bringt der Wrapper mit.

```bash
./gradlew bootRun
```

Unter Windows (cmd/PowerShell): `gradlew.bat bootRun`

Danach erreichbar:

| Adresse | Inhalt |
|---|---|
| `http://localhost:8080/` | Webclient |
| `http://localhost:8080/api/v1/stations` | REST-Schnittstelle |
| `http://localhost:8080/swagger-ui.html` | interaktive API-Dokumentation |
| `http://localhost:8080/v3/api-docs` | OpenAPI-Dokument (JSON) |

Die Anwendung braucht keine Datenbank und keine externen Dienste. Alle Daten
entstehen beim Start im Arbeitsspeicher und sind nach dem Beenden weg.

**Erster Eindruck:** Nach dem Start dauert es ein Simulationsintervall
(standardmäßig 10 Sekunden), bis der erste Messwert existiert. Vorher liefert
`/measurements/latest` einen 404 — das ist korrekt und kein Fehler.

### Für die Vorführung

Im Normalbetrieb stehen die Stationen zu etwa 75 % auf `NORMAL`. Um Warnungen
sofort zu sehen, gibt es ein Profil:

```bash
./gradlew bootRun --args="--spring.profiles.active=demo"
```

Das verkürzt das Intervall auf 500 ms und verstärkt den Regeneinfluss. Für den
Weg `ONLINE → MAINTENANCE → OFFLINE` zusätzlich:

```bash
./gradlew bootRun --args="--spring.profiles.active=demo --simulation.start-battery=20 --simulation.battery-drain-chance=1.0 --simulation.offline-chance=0.15"
```

### Tests

```bash
./gradlew test
```

### Build

```bash
./gradlew build
```

Erzeugt `build/libs/floodmonitor-0.0.1-SNAPSHOT.jar`, startbar mit
`java -jar build/libs/floodmonitor-0.0.1-SNAPSHOT.jar`.

---

## 2. Architektur

Fünf Schichten, jede mit genau einer Aufgabe. Abhängigkeiten zeigen immer nach
unten — eine tiefere Schicht kennt die darüberliegende nie.

```
  Client        src/main/resources/static/        HTML, CSS, jQuery
     │                                            holt Daten über HTTP
     ▼
  Controller    controller/                       HTTP: Pfade, Parameter, Status
     │                                            StationController, AlertController
     ▼
  Service       service/                          Geschäftslogik
     │                                            StationService, MeasurementService,
     │                                            AlertService, StatisticsService,
     │                                            WarningLevelCalculator, SimulationService
     ▼
  Repository    repository/                       Datenhaltung im Arbeitsspeicher
     │                                            StationRepository, MeasurementRepository
     ▼
  Model         model/                            Records und Enums, keine Logik

  quer dazu:
  Exception     exceptions/                       ApiError, ApiExceptionHandler
  Config        config/                           SimulationProperties, OpenApiConfig
```

### Warum diese Aufteilung

**Controller enthalten keine Geschäftslogik.** Jede Controller-Methode nimmt
Parameter entgegen und reicht sie an einen Service weiter — kein `if`, keine
Berechnung. Das ist eine Vorgabe der Angabe und hat einen praktischen Grund:
Logik im Controller lässt sich nur noch über HTTP testen, Logik im Service mit
einem gewöhnlichen Unit-Test.

**Die Warnstufenberechnung ist eine eigene Klasse ohne Spring-Bezug.**
`WarningLevelCalculator` wird im Test mit `new` erzeugt — siehe
`WarningLevelCalculatorTest`, der in Millisekunden statt Sekunden läuft.

**Filter liegen im Service, nicht im Controller.** Dadurch benutzen
`/measurements` und `/statistics` nachweislich dieselbe Filterlogik:
`StatisticsService` ruft `MeasurementService.history(...)` auf. Wären die Filter
dupliziert, könnten Tabelle und Kennzahlen auseinanderlaufen.

**Keine zyklischen Abhängigkeiten.** `MeasurementService` hängt an
`StationService` (für die 404-Prüfung). `StationService` braucht für den
Warnstufen-Filter die Messwerte — greift dafür aber direkt auf das
`MeasurementRepository` zu, nicht auf den `MeasurementService`. Der Umweg zurück
wäre ein Zyklus, den Spring beim Start mit `BeanCurrentlyInCreationException`
ablehnt.

### Verzeichnisse

```
floodmonitor/
├── build.gradle                        Abhängigkeiten und Build
├── README.md                           dieses Dokument
├── test.http                           38 manuelle Testaufrufe (IntelliJ HTTP-Client)
└── src/
    ├── main/
    │   ├── java/at/tgm/dezsys/floodmonitor/
    │   │   ├── FloodmonitorApplication.java
    │   │   ├── config/        SimulationProperties, OpenApiConfig, ApiErrorResponses
    │   │   ├── controller/    StationController, AlertController
    │   │   ├── exceptions/    ApiError, ApiExceptionHandler, 3 Exception-Typen
    │   │   ├── model/         Station, Location, Measurement, Alert, Statistics,
    │   │   │                  WarningLevel, StationStatus, 3 Listen-Hüllen
    │   │   ├── repository/    StationRepository, MeasurementRepository
    │   │   └── service/       6 Services
    │   └── resources/
    │       ├── application.properties
    │       ├── application-demo.properties      Profil für die Vorführung
    │       ├── application-example.properties   Konfigurationsvorlage
    │       └── static/        index.html, css/style.css, js/app.js
    └── test/
        ├── java/.../          StationApiTest, WarningLevelCalculatorTest, TestData
        └── resources/application.properties     simulation.enabled=false
```

---

## 3. Datenmodell

### Station

| Feld | Typ | Bedeutung |
|---|---|---|
| `id` | String | technische ID, z. B. `ST-001` |
| `name` | String | Anzeigename |
| `river` | String | Fluss |
| `location` | Location | geografische Position |
| `normalWaterLevel` | double | Normalpegel in cm |
| `warningWaterLevel` | double | ab hier `WARNING`, in cm |
| `criticalWaterLevel` | double | ab hier `CRITICAL`, in cm |
| `isActive` | boolean | Station in Betrieb? |

`Location` besteht aus `latitude` und `longitude` (beide `double`).

### Measurement

| Feld | Typ | Einheit |
|---|---|---|
| `stationId` | String | — |
| `timestamp` | `java.time.Instant` | ISO-8601, UTC |
| `waterLevel` | double | cm |
| `flowRate` | double | m³/s |
| `rainfall` | double | mm/h |
| `temperature` | double | °C |
| `batteryLevel` | int | % (0–100) |
| `status` | StationStatus | — |
| `warningLevel` | WarningLevel | — |

### Enums

* `WarningLevel`: `NORMAL`, `WARNING`, `CRITICAL`, `UNKNOWN`
* `StationStatus`: `ONLINE`, `MAINTENANCE`, `OFFLINE`

### Entwurfsentscheidungen

**`Instant` statt `LocalDateTime`.** Die Angabe verlangt UTC-Zeitstempel
(`2026-05-01T10:15:00Z`). `LocalDateTime` hat keine Zeitzone und würde das `Z`
beim Serialisieren verlieren.

**Alles `record`, nicht `class`.** Messwerte sind unveränderliche Tatsachen. Ein
Record erzwingt das: keine Setter, alle Felder final. Der Simulator schreibt aus
einem Hintergrund-Thread, während Tomcat-Threads lesen — unveränderliche Objekte
brauchen dafür keine Synchronisierung.

**Listen-Hüllen (`StationList`, `MeasurementList`, `AlertList`).** XML braucht
immer genau ein Wurzelelement. Eine nackte `List<Station>` lässt sich nach JSON
serialisieren, nach XML aber nicht sinnvoll benennen. Die Hüllen tragen
`@JacksonXmlElementWrapper(useWrapping = false)`, sonst entstünde die doppelte
Hülle `<stations><stations><station>…`.

**`Alert` ist kein `Measurement`.** Ein eigenes Read-Model: die Warnliste braucht
Stationsname und Fluss (sonst eine Nachfrage pro Warnung), dafür aber keine
Temperatur und keinen Akkustand.

### Stammdaten

Vier Stationen, angelegt im Konstruktor von `StationRepository`:

| ID | Name | Fluss | normal | Warnung | kritisch | aktiv |
|---|---|---|---|---|---|---|
| ST-001 | Donau Wien-Nussdorf | Donau | 280 | 310 | 360 | ja |
| ST-002 | Donau Krems | Donau | 265 | 300 | 350 | ja |
| ST-003 | Traisen St. Pölten | Traisen | 120 | 150 | 190 | ja |
| ST-004 | Ybbs Amstetten (außer Betrieb) | Ybbs | 95 | 130 | 170 | **nein** |

Die Angabe verlangt mindestens drei Stationen. ST-004 kommt dazu, damit
`isActive = false` nicht nur ein Feld im Datenmodell ist, sondern ein an der API
beobachtbarer Fall: Die Station erscheint unter `?isActive=false`, bekommt nie
einen Messwert, liefert bei `/measurements/latest` einen 404 und zählt bei
`?warningLevel=UNKNOWN` mit.

---

## 4. Simulationsregeln

`SimulationService` erzeugt alle `simulation.interval` Millisekunden für jede
aktive Station einen neuen Messwert. Jeder Wert baut auf dem vorherigen Wert
derselben Station auf — der Pegel springt nie zufällig.

| Größe | Regel |
|---|---|
| **Niederschlag** | klingt pro Schritt auf 80 % ab, plus leichtes Rauschen. Mit 8 % Wahrscheinlichkeit kommt ein Schauer von bis zu 12 mm/h dazu. Nie negativ. |
| **Wasserstand** | `neu = alt + Rücklauf + Regeneinfluss + Rauschen`, nach unten auf 0 begrenzt |
| → Rücklauf | `(Normalpegel − aktueller Pegel) × 0,25` |
| → Regeneinfluss | `Niederschlag × 1,8` cm |
| → Rauschen | Normalverteilung, σ = 1,5 cm |
| **Durchflussmenge** | vereinfachte Schlüsselkurve: `(Pegel / Normalpegel)^1,6 × Normalpegel × 2,5`. Wächst überproportional mit dem Pegel. |
| **Temperatur** | schwankt langsam um 14 °C |
| **Akkustand** | sinkt mit 5 % Wahrscheinlichkeit je Schritt um 1 %, bleibt in 0–100, **steigt nie** |
| **Status** | ab 15 % Akku oder weniger `MAINTENANCE`, sonst `ONLINE`. Aus `MAINTENANCE` fällt die Station mit 10 % Wahrscheinlichkeit je Schritt endgültig auf `OFFLINE`. |
| **Keine neuen Werte** | für Stationen mit `isActive = false` und für Stationen, deren letzter Status `OFFLINE` war |
| **Warnstufe** | kommt immer vom `WarningLevelCalculator`, nie aus einer Eingabe |

Alle genannten Zahlen sind Standardwerte und stehen in `SimulationProperties`
(Paket `config`). Sie lassen sich über `simulation.*` in
`application.properties` ändern, ohne den Java-Code anzufassen.

### Statusübergänge

```
   ONLINE ──Akku ≤ 15 %──► MAINTENANCE ──10 % je Schritt──► OFFLINE
                                                               │
                                   keine neuen Messwerte mehr ─┘
```

Ein Einwegverfall: Aus `OFFLINE` führt kein Weg zurück, weil der Simulator für
eine solche Station nichts mehr erzeugt. Dort müsste ein Techniker hin.

### Warum `mean-reversion` = 0,25

Rücklauf und Regeneinfluss müssen zueinander passen. Der Regen hebt den Pegel um
`rain-to-level × Niederschlag`, der Rücklauf zieht ihn um
`mean-reversion × (Normalpegel − Pegel)` zurück. Das Gleichgewicht liegt dort,
wo sich beides aufhebt.

Mit dem ursprünglichen Wert 0,05 lag dieses Gleichgewicht bei rund 420 cm —
dauerhaft über dem kritischen Wert **jeder** Station. Gemessen über 449
Messwerte: 369 davon `CRITICAL`, nur 3,5 % `NORMAL`, Spitzenpegel 633 cm. Der
Filter `?warningLevel=NORMAL` lieferte nie etwas, und der Webclient war dauerhaft
rot.

Mit 0,25 verteilen sich die Warnstufen realistisch. Gemessen über je ~1000
Messwerte pro Station:

| Station | min | ⌀ | max | NORMAL | WARNING | CRITICAL |
|---|---|---|---|---|---|---|
| ST-001 (280/310/360) | 276 | 299 | 356 | 77,0 % | 23,0 % | 0,0 % |
| ST-002 (265/300/350) | 263 | 288 | 349 | 76,9 % | 23,1 % | 0,0 % |
| ST-003 (120/150/190) | 119 | 137 | 202 | 82,0 % | 16,9 % | 1,1 % |

Zugesicherte Invarianten, über denselben Lauf geprüft: kein negativer Pegel,
Akkustand durchgehend zwischen 0 und 100.

---

## 5. Warnstufen

Die Warnstufe wird **ausschließlich serverseitig** berechnet und kann vom
Client nicht vorgegeben werden. Es gibt keinen schreibenden Endpunkt, über den
sie gesetzt werden könnte.

| Bedingung | Warnstufe |
|---|---|
| Wasserstand < Warnwert | `NORMAL` |
| Wasserstand ≥ Warnwert | `WARNING` |
| Wasserstand ≥ kritischem Wert | `CRITICAL` |
| Station `OFFLINE` | `UNKNOWN` |
| keine Messung vorhanden | `UNKNOWN` |
| Messung älter als `floodmonitor.measurement-max-age` (Standard 5 min) | `UNKNOWN` |

Die Schwellwerte gelten **inklusive**: ein Pegel von exakt 310 cm bei einem
Warnwert von 310 cm ist bereits `WARNING`.

### Gespeicherte und aktuell gültige Warnstufe

`WarningLevelCalculator` hat zwei Aufgaben, die man auseinanderhalten muss:

* `calculate(...)` berechnet die Stufe beim **Erzeugen** eines Messwerts. Dieser
  Wert wird im `Measurement` gespeichert und ändert sich nie wieder — auch dann
  nicht, wenn später jemand die Schwellwerte der Station anpasst.
* `effective(measurement)` liefert die Stufe, die **jetzt** gilt. Sie entspricht
  der gespeicherten, außer die Messung ist zu alt: dann `UNKNOWN`.

Die Altersgrenze setzt die Vorgabe „keine **aktuelle** oder gültige Messung" um.
Ohne sie würde eine Station, die vor zwei Tagen zuletzt `NORMAL` gemeldet hat,
weiterhin als `NORMAL` gelten — falsche Sicherheit in einem Frühwarnsystem.

`effective(...)` wird für `?warningLevel=` an `/stations` und für `/alerts`
benutzt. Die Historie zeigt dagegen immer die gespeicherten Werte, denn sie ist
eine Aufzeichnung der Vergangenheit.

---

## 6. REST-Schnittstelle

Basis-URL: `/api/v1`

| Methode | Endpunkt | Beschreibung |
|---|---|---|
| GET | `/api/v1/stations` | alle Messstationen |
| GET | `/api/v1/stations/{stationId}` | einzelne Station |
| GET | `/api/v1/stations/{stationId}/measurements/latest` | aktuellster Messwert |
| GET | `/api/v1/stations/{stationId}/measurements` | historische Messwerte |
| GET | `/api/v1/stations/{stationId}/statistics` | statistische Auswertung |
| GET | `/api/v1/alerts` | aktuelle Warnungen |

Alle Endpunkte sind lesend. Es gibt bewusst keine schreibenden Operationen —
die Angabe fordert keine, und ohne sie kann die Warnstufe nicht von außen
manipuliert werden.

### Filter

| Endpunkt | Parameter | Werte |
|---|---|---|
| `/stations` | `river` | Flussname, Schreibweise egal |
| | `isActive` | `true` / `false` |
| | `warningLevel` | `NORMAL` / `WARNING` / `CRITICAL` / `UNKNOWN` |
| `/stations/{id}/measurements` | `from` | ISO-8601, inklusive |
| `/stations/{id}/statistics` | `to` | ISO-8601, inklusive |
| | `limit` | 1 bis 1000 |
| | `warningLevel` | wie oben |
| `/alerts` | `warningLevel` | wie oben |
| | `river` | Flussname |

Alle Parameter sind optional und beliebig kombinierbar. Ein nicht angegebener
Parameter filtert nicht.

```
GET /api/v1/stations?river=Donau
GET /api/v1/stations?isActive=true
GET /api/v1/stations?warningLevel=CRITICAL
GET /api/v1/stations?river=Donau&isActive=true&warningLevel=WARNING

GET /api/v1/stations/ST-001/measurements?from=2026-05-01T08:00:00Z&to=2026-05-01T12:00:00Z&limit=100
GET /api/v1/stations/ST-001/measurements?warningLevel=CRITICAL&limit=10
GET /api/v1/stations/ST-001/statistics?limit=50

GET /api/v1/alerts?warningLevel=CRITICAL
GET /api/v1/alerts?river=Donau
```

### Festlegungen

**Leeres Ergebnis ist 200, nicht 404.** `?river=Nil` liefert eine leere Liste.
Die Sammlung `/stations` existiert — sie enthält zu diesen Kriterien nur nichts.
Ein 404 wäre die Aussage „diesen Endpunkt gibt es nicht".

**`limit` schneidet vom Ende.** `?limit=10` liefert die *neuesten* zehn
Messwerte, weiterhin chronologisch sortiert. Ein Monitoring-Client will das
Aktuellste sehen, nicht das Älteste.

**Zeitgrenzen sind inklusive.** Ein Messwert exakt auf `from` oder `to` ist
enthalten.

**`+` in Zeitstempeln muss kodiert werden.** In einer Query bedeutet `+` ein
Leerzeichen. `2026-05-01T08:00:00+02:00` muss als
`2026-05-01T08:00:00%2B02:00` gesendet werden. Mit `Z` (UTC) tritt das Problem
nicht auf.

**Was gilt als Warnung?** `/alerts` liefert für jede Station, deren letzter
Messwert eine Warnstufe ungleich `NORMAL` hat, einen Eintrag — sortiert nach
Dringlichkeit `CRITICAL`, `WARNING`, `UNKNOWN`. `UNKNOWN` ist absichtlich dabei:
eine Station, die nicht mehr meldet, ist für ein Frühwarnsystem kein
Nicht-Ereignis, sondern eine Lücke in der Überwachung. Stationen, die noch nie
gemeldet haben, erscheinen nicht — zu ihnen gibt es weder Pegel noch Zeitpunkt.
`?warningLevel=NORMAL` liefert folgerichtig eine leere Liste mit Status 200.

**Statistik bei leerer Menge.** 200 mit `measurementCount = 0`; die übrigen
Zahlen sind dann 0 und ohne Aussage. Ein 404 würde behaupten, die Station
existiere nicht.

**`warningCount` zählt `criticalCount` mit.** „Anzahl der Warnungen" meint alle
Messwerte, bei denen gewarnt wurde; `criticalCount` ist davon die Teilmenge.

---

## 7. JSON und XML

Die Schnittstelle liefert standardmäßig JSON und unterstützt XML. Das Format
wählt der Client über den `Accept`-Header:

```
Accept: application/json     → JSON
Accept: application/xml      → XML
(kein Accept-Header)         → JSON
Accept: text/csv             → 406 Not Acceptable
```

Technisch macht das Spring selbst: `@RestController` serialisiert den
Rückgabewert über den passenden `HttpMessageConverter`, und welcher das ist,
entscheidet die Content Negotiation anhand des `Accept`-Headers. Im Code steht
dafür keine einzige Fallunterscheidung — nur die Abhängigkeit
`jackson-dataformat-xml` und `produces = {APPLICATION_JSON_VALUE,
APPLICATION_XML_VALUE}` an den Mappings.

JSON und XML enthalten dieselben fachlichen Informationen.

### Beispiel: aktueller Messwert

```bash
curl -H "Accept: application/json" http://localhost:8080/api/v1/stations/ST-001/measurements/latest
```

```json
{
    "stationId": "ST-001",
    "timestamp": "2026-10-01T12:58:56.372012Z",
    "waterLevel": 303.1,
    "flowRate": 794.7,
    "rainfall": 0.4,
    "temperature": 14.1,
    "batteryLevel": 98,
    "status": "ONLINE",
    "warningLevel": "NORMAL"
}
```

```bash
curl -H "Accept: application/xml" http://localhost:8080/api/v1/stations/ST-001/measurements/latest
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<measurement>
  <stationId>ST-001</stationId>
  <timestamp>2026-10-01T12:58:56.372012Z</timestamp>
  <waterLevel>303.1</waterLevel>
  <flowRate>794.7</flowRate>
  <rainfall>0.4</rainfall>
  <temperature>14.1</temperature>
  <batteryLevel>98</batteryLevel>
  <status>ONLINE</status>
  <warningLevel>NORMAL</warningLevel>
</measurement>
```

### Beispiel: Station

```json
{
    "id": "ST-003",
    "name": "Traisen St. Poelten",
    "river": "Traisen",
    "location": {
        "latitude": 48.2,
        "longitude": 15.63
    },
    "normalWaterLevel": 120.0,
    "warningWaterLevel": 150.0,
    "criticalWaterLevel": 190.0,
    "isActive": true
}
```

### Beispiel: Warnungen

```json
{
    "alerts": [
        {
            "stationId": "ST-002",
            "stationName": "Donau Krems",
            "river": "Donau",
            "warningLevel": "WARNING",
            "waterLevel": 307.2,
            "warningWaterLevel": 300.0,
            "criticalWaterLevel": 350.0,
            "timestamp": "2026-10-01T12:59:02.776371300Z"
        }
    ]
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<alerts>
  <alert>
    <stationId>ST-003</stationId>
    <stationName>Traisen St. Poelten</stationName>
    <river>Traisen</river>
    <warningLevel>WARNING</warningLevel>
    <waterLevel>176.4</waterLevel>
    <warningWaterLevel>150.0</warningWaterLevel>
    <criticalWaterLevel>190.0</criticalWaterLevel>
    <timestamp>2026-10-01T12:59:03.181585500Z</timestamp>
  </alert>
</alerts>
```

### Beispiel: Statistik

```json
{
    "stationId": "ST-001",
    "measurementCount": 33,
    "minWaterLevel": 277.7,
    "maxWaterLevel": 364.0,
    "avgWaterLevel": 321.5,
    "avgFlowRate": 876.6,
    "totalRainfall": 94.0,
    "warningCount": 20,
    "criticalCount": 3
}
```

Weitere 38 fertige Aufrufe stehen in [`test.http`](test.http) und lassen sich im
IntelliJ-HTTP-Client direkt ausführen.

---

## 8. Fehlerbehandlung

Zentral in `ApiExceptionHandler` mit `@RestControllerAdvice`. Jeder Fehler
liefert denselben `ApiError`-Body — in JSON oder XML, je nach `Accept`-Header.
**Stacktraces werden nie ausgeliefert**, sie stehen ausschließlich im Server-Log.

```json
{
    "timestamp": "2026-10-01T12:59:03.697906Z",
    "status": 404,
    "error": "Not Found",
    "message": "The station with id ST-999 does not exist",
    "path": "/api/v1/stations/ST-999"
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<error>
  <timestamp>2026-10-01T12:59:04.106460400Z</timestamp>
  <status>400</status>
  <error>Bad Request</error>
  <message>muss kleiner-gleich 1000 sein</message>
  <path>/api/v1/stations/ST-001/measurements</path>
</error>
```

### Behandelte Fälle

| Situation | Status | ausgelöst durch |
|---|---|---|
| Anfrage erfolgreich | 200 | — |
| unbekannte Stations-ID | 404 | `StationNotFoundException` |
| Station ohne Messwert | 404 | `MeasurementNotFoundException` |
| unbekannter Pfad | 404 | `NoResourceFoundException` |
| `from` liegt nach `to` | 400 | `InvalidRangeException` |
| `limit` außerhalb 1–1000 | 400 | `HandlerMethodValidationException` |
| `limit` keine Zahl | 400 | `MethodArgumentTypeMismatchException` |
| ungültiges Datumsformat | 400 | `MethodArgumentTypeMismatchException` |
| ungültige Warnstufe | 400 | `MethodArgumentTypeMismatchException` |
| fehlender Pflichtparameter | 400 | `MissingServletRequestParameterException` |
| falsches HTTP-Verb | 405 | `HttpRequestMethodNotSupportedException` |
| nicht unterstütztes Format | 406 | `HttpMediaTypeNotAcceptableException` |
| nicht unterstützter Content-Type | 415 | `HttpMediaTypeNotSupportedException` |
| alles andere | 500 | Catch-all, Stacktrace nur ins Log |

### Nicht auftretende Statuscodes

Die Angabe listet auch 201, 204, 401, 403 und 409 auf. Diese Codes können hier
nicht entstehen, und ein künstlicher Endpunkt nur zu ihrer Vorführung wäre
unehrlich:

* **201 / 204 / 409** setzen schreibende Operationen voraus. Alle geforderten
  Endpunkte sind `GET`.
* **401 / 403** setzen Authentifizierung voraus. Die API ist bewusst offen —
  siehe [Offene Punkte](#14-offene-punkte).

### Zwei Besonderheiten

**406 antwortet immer in JSON.** Der Client hat ein Format verlangt, das die API
nicht erzeugen kann. Würde sie versuchen, die Fehlermeldung in genau diesem
Format zu liefern, fände sie wieder keinen Converter und der Handler liefe ins
Leere. Deshalb erzwingt `handleNotAcceptable` den Content-Type.

**Der Catch-all ist die gefährlichste Methode der Klasse.** Ohne eigene Handler
für Springs Web-Exceptions macht `@ExceptionHandler(Exception.class)` aus einem
Tippfehler in der URL (eigentlich 404) und einem falschen HTTP-Verb (eigentlich
405) ein **500** — der Server behauptet dann, *er* habe einen Fehler, obwohl der
Client falsch gefragt hat. Als Sicherheitsnetz prüft der Catch-all zusätzlich, ob
die Exception das Interface `ErrorResponse` implementiert, und übernimmt dann
deren Status statt 500.

---

## 9. Webclient

Erreichbar unter `http://localhost:8080/`. Reines HTML, CSS und jQuery, ohne
Build-Schritt. Liegt in `src/main/resources/static/` und wird von Spring Boot
automatisch ausgeliefert — dadurch läuft der Client auf derselben Herkunft wie
die API, es gibt keine CORS-Problematik.

### Funktionen

* Stationen laden, Auswahl per Dropdown
* aktueller Messwert der gewählten Station
* historische Messwerte als Tabelle
* Filter nach Zeitraum, Warnstufe und Anzahl
* Umschalter zwischen JSON und XML
* automatische Aktualisierung (5 / 10 / 30 Sekunden, abschaltbar)
* Ladeanzeige und verständliche Fehlermeldungen
* aktuelle Warnungen, filterbar nach Warnstufe und Fluss
* Statistik zur gerade gefilterten Menge

Die Tabelle enthält Station, Zeitpunkt, Wasserstand, Durchflussmenge,
Niederschlag, Akkustand, Stationsstatus und Warnstufe. Farbcodierung laut
Angabe: `NORMAL` grün, `WARNING` orange, `CRITICAL` rot, `UNKNOWN` grau — als
Badge **mit Text**, denn Farbe allein wäre für Benutzer mit Farbsehschwäche keine
Information.

### Aufbau von `app.js`

Der Client muss dieselben Daten aus JSON *und* XML darstellen. Zwei Render-Pfade
würden bedeuten, dass jede Änderung an der Tabelle zweimal gemacht werden muss
und die Ansichten auseinanderlaufen. Stattdessen drei Schichten:

```
  api.*      holt Daten            kennt HTTP und den Accept-Header
  parse.*    JSON oder XML  ──►    immer dasselbe JS-Objekt
  render.*   zeichnet               sieht nie ein XML-Dokument
```

Nur `parse.*` weiß überhaupt, dass es zwei Formate gibt. Deshalb ist garantiert,
dass JSON und XML dieselbe Darstellung ergeben — es gibt nur eine Darstellung.
Verifiziert durch Umschalten im laufenden Client: der Seiteninhalt ist in beiden
Modi zeichengleich.

Fehlermeldungen werden ebenfalls aus beiden Formaten gelesen. Im XML-Modus zeigt
der Client die Meldung aus `<error><message>` des Servers, nicht nur eine
Statusnummer.

### jQuery

Wird vom CDN `code.jquery.com` geladen (mit Subresource-Integrity-Prüfung).
Schlägt das fehl, zeigt die Seite einen Hinweis statt leer zu bleiben. Für den
Betrieb ohne Internet jQuery herunterladen, unter
`src/main/resources/static/js/jquery.min.js` ablegen und in `index.html` das
`<script src="…">` auf diesen Pfad ändern.

---

## 10. API-Dokumentation

Generiert mit **springdoc-openapi 3.1.1**. Die Version ist wichtig: Die
verbreitete 2.x-Linie ist auf Spring Boot 3 gebaut und scheitert unter Boot 4
beim Start.

| Adresse | Inhalt |
|---|---|
| `http://localhost:8080/swagger-ui.html` | interaktive Oberfläche zum Ausprobieren |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3.1 als JSON |
| `http://localhost:8080/v3/api-docs.yaml` | dasselbe als YAML |

Dokumentiert sind alle sechs Endpunkte mit Methode, Parametern (inklusive
Beschreibung und Beispielwert), Response-Bodies, beiden Formaten
(`application/json` und `application/xml`), allen Statuscodes und dem
`ApiError`-Schema für Fehlerantworten. Die Kopfdaten samt Hinweis zur fehlenden
Authentifizierung stehen in `OpenApiConfig`.

Die drei an jedem Endpunkt möglichen Fehlerantworten (400, 406, 500) stehen
nicht sechsmal im Code, sondern einmal als zusammengesetzte Annotation
`@ApiErrorResponses` — dasselbe Muster, mit dem Spring `@RestController` aus
`@Controller` und `@ResponseBody` zusammensetzt.

### Statisches Dokument erzeugen

```bash
curl http://localhost:8080/v3/api-docs.yaml -o docs/openapi.yaml
```

---

## 11. Automatisierte Tests

```bash
./gradlew test
```

**51 Tests, Laufzeit ca. 4,5 Sekunden.** Der HTML-Bericht liegt danach unter
`build/reports/tests/test/index.html`.

### Die zwölf geforderten Fälle

| # | Fall | Test |
|---|---|---|
| 1 | alle Stationen als JSON | `allStationsAsJson` |
| 2 | alle Stationen als XML | `allStationsAsXml` |
| 3 | existierende Station | `existingStation` |
| 4 | unbekannte Station | `unknownStation` |
| 5 | aktuellster Messwert | `latestMeasurement` |
| 6 | Filterung nach Zeitraum | `filterByTimeRange` |
| 7 | ungültiger Zeitraum | `invalidTimeRange` |
| 8 | Warnstufe NORMAL | `WarningLevels.normal` |
| 9 | Warnstufe WARNING | `WarningLevels.warning` |
| 10 | Warnstufe CRITICAL | `WarningLevels.critical` |
| 11 | nicht unterstütztes Format | `unsupportedFormat` |
| 12 | maximales Limit | `Limit.aboveMaximum` |

Dazu Tests für Stationsfilter, Warnungen, Statistik, inklusive Schwellwerte,
XML-Fehlerantworten, unbekannte Pfade und falsche HTTP-Verben.

### Reproduzierbarkeit

Die Angabe verlangt, dass Tests nicht zufällig fehlschlagen. Zwei Maßnahmen:

**Simulator abgeschaltet.** `src/test/resources/application.properties` setzt
`simulation.enabled=false`. Technisch greift `@ConditionalOnProperty` am
`SimulationService`: die Bean wird gar nicht erst angelegt. Liefe der Simulator
mit, würde ein Test, der „genau 3 Messwerte" erwartet, mal grün und mal rot sein.

**Feste Zeitstempel.** Alle Testdaten leiten sich von
`TestData.T0 = 2026-05-01T08:00:00Z` ab. Ein `Instant.now()` im Testaufbau würde
die Zeitraumfilter von der Uhr der Testmaschine abhängig machen.

Das Repository ist eine Singleton-Bean und wird zwischen Tests
wiederverwendet — `@BeforeEach` ruft deshalb `measurements.clear()`, sonst hinge
das Ergebnis von der Ausführungsreihenfolge ab.

Drei vollständige Durchläufe aus sauberem Zustand: 51/51 grün, jeweils ~4,5 s.

### Warum `@SpringBootTest` und nicht `@WebMvcTest`

`@WebMvcTest` startet nur die Web-Schicht und ersetzt die Services durch
Attrappen. Zwei der geforderten Fälle — „alle Stationen als XML" und „nicht
unterstütztes Format" — prüfen aber genau das, was *außerhalb* des Controllers
passiert: welcher `HttpMessageConverter` greift und wie die Content Negotiation
entscheidet. Mit Attrappen wäre das nicht getestet.

`WarningLevelCalculatorTest` dagegen kommt ganz ohne Spring aus und erzeugt die
Klasse mit `new` — das ist der praktische Gewinn daraus, dass die Berechnung
keine Spring-Abhängigkeiten hat.

---

## 12. Konfiguration

Alle Schalter stehen mit Erklärung in
[`application-example.properties`](src/main/resources/application-example.properties).
Die wichtigsten:

| Schlüssel | Standard | Wirkung |
|---|---|---|
| `server.port` | 8080 | Port |
| `simulation.interval` | 10000 | Abstand zwischen Simulationsschritten in ms |
| `simulation.enabled` | true | false legt den Simulator gar nicht erst an |
| `simulation.mean-reversion` | 0.25 | Rücklauf zum Normalpegel je Schritt |
| `simulation.rain-to-level` | 1.8 | Pegelanstieg in cm je mm/h Regen |
| `simulation.level-noise-sd` | 1.5 | Rauschen auf dem Pegel in cm |
| `simulation.shower-chance` | 0.08 | Wahrscheinlichkeit eines Schauers je Schritt |
| `simulation.shower-intensity` | 12.0 | maximale Regenmenge eines Schauers in mm/h |
| `simulation.maintenance-battery` | 15 | Akkuschwelle für MAINTENANCE in % |
| `simulation.start-battery` | 100 | Akkustand der ersten Messung in % |
| `simulation.battery-drain-chance` | 0.05 | Wahrscheinlichkeit, dass der Akku je Schritt um 1 % sinkt |
| `simulation.offline-chance` | 0.1 | Wahrscheinlichkeit MAINTENANCE → OFFLINE je Schritt |
| `floodmonitor.measurement-max-age` | PT5M | ab diesem Alter gilt eine Messung als nicht mehr aktuell |

### Geheime Zugangsdaten

**Es gibt keine.** Keine Datenbank, kein externer Dienst, keine API-Schlüssel,
keine Authentifizierung — alle Daten entstehen im Arbeitsspeicher. Deshalb ist
`application.properties` unbedenklich im Repository. Käme später ein Passwort
dazu, gehörte es in eine Umgebungsvariable oder eine nicht versionierte Datei,
nicht in diese Dateien.

### Profile

| Profil | Zweck |
|---|---|
| (keines) | Normalbetrieb |
| `demo` | schnelles Intervall und starker Regen für die Vorführung |

```bash
./gradlew bootRun --args="--spring.profiles.active=demo"
```

Einzelne Werte lassen sich auch ohne Profil überschreiben:

```bash
./gradlew bootRun --args="--simulation.interval=1000 --simulation.rain-to-level=5.0"
```

---

## 13. Reflexion

### Jackson 3 statt Jackson 2

Spring Boot 4 nutzt Jackson 3. Die Gruppen-ID hat sich von
`com.fasterxml.jackson` auf `tools.jackson` geändert. Die alte Koordinate wird
vom Boot-4-BOM nicht mehr verwaltet und hätte in `build.gradle` keine Version —
der Build bricht ab. Betroffen sind auch alle Imports:
`tools.jackson.dataformat.xml.annotation.*`. Praktisch jede Anleitung im Netz
nennt noch die alte Koordinate.

### Doppelte XML-Hülle

Der erste Versuch mit `List<Station>` in einer Hülle erzeugte
`<stations><stations><station>…`. Jackson wickelt Listen in XML standardmäßig
zusätzlich ein. `@JacksonXmlElementWrapper(useWrapping = false)` am Listenfeld
behebt das.

### `@Validated` hätte 400 zu 500 gemacht

Naheliegend war, für die `@Min`/`@Max`-Prüfung an `limit` ein `@Validated` an die
Controller-Klasse zu schreiben. Das wäre falsch gewesen: Seit Spring Framework
6.1 prüfen Controller Parameter-Constraints von selbst und werfen
`HandlerMethodValidationException` — genau die, für die der `ApiExceptionHandler`
einen 400-Handler hat. Mit `@Validated` greift stattdessen der ältere AOP-Weg mit
`ConstraintViolationException`, für die es keinen Handler gibt. Aus 400 wäre 500
geworden. Aufgefallen beim Nachprüfen am laufenden Server.

### Der Catch-all verschluckte 404 und 405

`@ExceptionHandler(Exception.class)` fängt auch Springs eigene Web-Exceptions.
Ein Tippfehler in der URL und ein falsches HTTP-Verb wurden dadurch beide zu
einem 500. Aufgefallen erst beim systematischen Durchtesten der Fehlerfälle —
die „normalen" Fälle sahen alle richtig aus. Behoben durch eigene Handler für
`NoResourceFoundException` und `HttpRequestMethodNotSupportedException` plus eine
`ErrorResponse`-Prüfung im Catch-all.

### Die Simulation lief weg

Mit `mean-reversion = 0.05` und `rain-to-level = 1.8` lag das Gleichgewicht des
Pegels bei rund 420 cm — über dem kritischen Wert jeder Station. Die Werte waren
einzeln plausibel, erst ihr Zusammenspiel war falsch. Beim Testen fiel auf, dass
`?warningLevel=NORMAL` nie etwas lieferte; die Messung über 449 Messwerte zeigte
dann 55 % `CRITICAL` und 3,5 % `NORMAL`. Behoben durch `mean-reversion = 0.25`,
nachgerechnet und am laufenden System nachgemessen.

Lehre daraus: Bei einem Simulationsmodell reicht es nicht, die Einzelregeln zu
prüfen. Erst die Verteilung über viele Schritte zeigt, ob das Modell stabil ist.

### `StationStatus.OFFLINE` war unerreichbar

Der Simulator setzte nur `ONLINE` und `MAINTENANCE`. Die OFFLINE-Prüfung in
`simulate()` war damit toter Code, und `UNKNOWN` trat nie auf. Ergänzt um einen
Einwegverfall `MAINTENANCE → OFFLINE` und um die deaktivierte Station ST-004.

### CSS schlägt `hidden`

Der Fehlerbalken im Webclient war dauerhaft sichtbar, obwohl `hidden` gesetzt
war. Ursache: Das Attribut wirkt über die Regel `[hidden] { display: none }` im
Stylesheet des *Browsers*, und eigene Regeln des Dokuments gewinnen immer gegen
das Browser-Stylesheet — unabhängig von der Spezifität. Das eigene
`.errorbox { display: flex }` machte `hidden` wirkungslos. Behoben mit
`[hidden] { display: none !important; }`.

### Boot 4 hat Testannotationen verschoben

`@AutoConfigureMockMvc` liegt in Boot 4 unter
`org.springframework.boot.webmvc.test.autoconfigure`. In Boot 3 war es
`org.springframework.boot.test.autoconfigure.web.servlet`. Jede Anleitung nennt
noch den alten Pfad.

### Gradle und Windows

Zwei Eigenheiten, beide nicht am Projekt:

* Der Gradle-Daemon hält unter Windows Verzeichnis-Handles auf
  `build/classes/java/main`. `./gradlew clean` scheitert dann mit „Unable to
  delete directory". Abhilfe: `./gradlew --stop` vor dem Clean. Aus demselben
  Grund ist `spring-boot-devtools` in `build.gradle` auskommentiert — sein
  Restart-Watcher verschärft das Problem.
* Steht im `TEMP`-Pfad ein 8.3-Kurzname (`BENUTZ~1`), scheitert Javas
  `Selector.open()` beim Anlegen eines Unix-Domain-Sockets, und Gradle meldet
  „Unable to establish loopback connection". Abhilfe: `TEMP` auf einen kurzen
  Pfad ohne Tilde setzen. In IntelliJ tritt das nicht auf.

---

## 14. Offene Punkte

Ehrlich benannt, statt vorzutäuschen, es sei alles abgedeckt:

* **Keine Authentifizierung.** Die Lernziele nennen „grundlegende
  Sicherheitsmaßnahmen für APIs". Umgesetzt sind davon: keine Stacktraces nach
  außen, strikte Eingabevalidierung an jedem Parameter, keine schreibenden
  Endpunkte (die Warnstufe ist dadurch von außen nicht manipulierbar), HTML-
  Escaping im Webclient und Subresource Integrity beim CDN-Skript. Eine
  Authentifizierung mit Spring Security und damit die Statuscodes 401 und 403
  fehlen; `spring-boot-starter-security` ist in `build.gradle` auskommentiert
  vorbereitet.
* **Keine Persistenz.** Alle Daten liegen im Arbeitsspeicher und sind nach dem
  Beenden weg. Für die Aufgabenstellung ausreichend, für einen echten Betrieb
  nicht.
* **Historie begrenzt** auf 1000 Messwerte je Station
  (`MeasurementRepository.MAX_HISTORY_PER_STATION`), damit der Speicher im
  Dauerbetrieb nicht wächst.
* **Screenshots des Webclients** fehlen noch in der Abgabe.
* **Die zwölf Fragestellungen** aus Punkt 12 der Angabe sind noch nicht
  beantwortet (Quellverweise erforderlich).
* **Dokumentation der AI-Prompts** fehlt noch.
* **Deprecation-Warnung:** `@JacksonXmlRootElement` ist in Jackson 3 als veraltet
  markiert; der vorgesehene Ersatz ist `@JsonRootName`. Funktioniert derzeit
  fehlerfrei.
