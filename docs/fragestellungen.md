# Fragestellungen & Antworten

---

### 1. Was sind die Hauptmerkmale von JSON?

#### Antwort:
##### Generell
* Erweiterbarkeit (neue Tags können definiert werden)
* Strukturierbarkeit (Tags können beliebig verschachtelt werden)
* Layoutunabhängigkeit (die Anzeige der Daten liegt nicht in der Verantwortung von XML)
##### Vorteile gegenüber JSON
* Validierbarkeit (ein einfacher Parser kann XML auf wohlgeformtheit prüfen)
* Semistrukturiertheit (es können auch nicht XML Teile eingefügt werden)

---

### 2. Was sind die Hauptmerkmale von XML?

#### Antwort:
##### Generell
* Strukturierbarkeit (Tags können beliebig verschachtelt werden)
* Layoutunabhängigkeit (die Anzeige der Daten liegt nicht in der Verantwortung von XML)
##### Vorteile gegenüber JSON
* Lightweight (gleiche Daten verbrauchen weniger Speicherplatz in JSON als in XML)
* Leicht lesbar
---

### 3. Wie unterscheidet die Applikation in diesem Beispiel, ob die Rückgabewerte in JSON oder XML zurückgegeben werden?

#### Antwort:
Anhand eines Headers in der Angabe `Accept: application/xml`. Per default: JSON

---

### 4. Was zeichnet ein REST-Interface aus und warum wird es heute sehr oft als standardisierte Schnittstelle verwendet?

#### Antwort:
* Stateless (und dadurch leicht Skalierbar)
* URL-based über HTTP
* HTTP Methoden (GET; POST; PUT; PATCH)
* Beliebiges Rückgabeformat (oft JSON)
---

### 5. Was ist der Unterschied zwischen den HTTP-Methoden GET, POST, PUT und PATCH?

#### Antwort:
* **GET:**
* Ressourcen abrufen (oft mit filtern)
* **POST:**
* Ressourcen erstellen (nicht-idempotent)
* **PUT:**
* Ressourcen erstellen oder ersetzen (idempotent)
* **PATCH:**
* Ressourcen ändern

![HTTP-Methoden.png](HTTP-Methoden.png)
---

### 6. Ich will eine neue Request-Methode / einen neuen Pfad in meinem REST-Interface zur Verfügung stellen. Welche Stellen des Quellcodes muss ich verändern?

#### Antwort:
In `src/main/java/at/tgm/dezsys/floodmonitor/controller` entweder in `AlertController.java` oder in `StationController.java` oder durch erstellen einer eigenen Klasse mit der Annotation `@RestController`. Dort kann dann eine Methode wie die folgende hinzugefügt werden:
```java
@GetMapping(value = "/stations/{stationId}/statistics", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
public Statistics statistics() {
    return repo.getStatistics();
}
```

---

### 7. Kann man OpenAPI und Swagger vergleichen? Wenn ja, vergleichen Sie beide Themengebiete. Wenn nein, begründen Sie die Antwort.

#### Antwort:
Nein nicht wirklich. Zumindest im Kontext von Spring. OpenAPI ist ein Dokumentationsstandart, damit kann man aus Endpoints eine JSON Doku erstellen welche dann z.B. Tools zum frontend Builden aufgreifen können. Swagger-UI ist dann eine Extra Schicht mit der man dann im Browser die Endpoints schön ansehen und ausprobieren kann.

---

### 8. Wo liegt im folgenden Code-Snippet der Fehler? Begründe die Antwort.

```java
@GetMapping("/stations/add")
public String createStation(@RequestParam String name, @RequestParam double warning) {
    stationService.save(new Station(name, warning));
    return "Station successfully created!";
}
```

#### Antwort:
##### Schwerer Fehler: 
GET wird fürs erstellen der Elemente verwendet. Es muss aber POST verwendet werden
##### Leicher Fehler:
Eigentlich sollte *201 Created* zurück gegeben werden und kein Text

