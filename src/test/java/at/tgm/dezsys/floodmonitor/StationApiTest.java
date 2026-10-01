package at.tgm.dezsys.floodmonitor;

import at.tgm.dezsys.floodmonitor.model.StationStatus;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import at.tgm.dezsys.floodmonitor.repository.MeasurementRepository;
import at.tgm.dezsys.floodmonitor.service.MeasurementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

/**
 * Tests der REST-Schnittstelle (Angabe Punkt 9).
 *
 * <h2>Warum {@code @SpringBootTest} und nicht {@code @WebMvcTest}?</h2>
 * {@code @WebMvcTest} startet nur die Web-Schicht und ersetzt die Services
 * durch Attrappen. Zwei der geforderten Testfaelle - "Abruf aller Stationen als
 * XML" und "nicht unterstuetztes Format" - pruefen aber genau das, was
 * <em>ausserhalb</em> des Controllers passiert: welcher HttpMessageConverter
 * greift, und wie die Content Negotiation entscheidet. Mit Attrappen waere das
 * nicht getestet. {@code @SpringBootTest} faehrt den vollstaendigen Context
 * hoch, inklusive Jackson-XML.
 *
 * <p>{@code @AutoConfigureMockMvc} liefert ein {@link MockMvc}, das Requests
 * direkt durch die Spring-MVC-Infrastruktur schickt - ohne Netzwerk, ohne Port,
 * ohne Tomcat. Deshalb laufen diese Tests in Sekunden statt Minuten.
 *
 * <p><b>Boot-4-Falle:</b> {@code @AutoConfigureMockMvc} liegt hier in
 * {@code org.springframework.boot.webmvc.test.autoconfigure}. In Boot 3 war es
 * {@code org.springframework.boot.test.autoconfigure.web.servlet} - jede
 * Anleitung im Netz nennt noch den alten Pfad.
 *
 * <h2>Reproduzierbarkeit</h2>
 * {@code src/test/resources/application.properties} setzt
 * {@code simulation.enabled=false}. Der Simulator laeuft also nicht, und jeder
 * Test legt seine Daten in {@link #setUp()} selbst an. Ohne das wuerden
 * Zusicherungen auf konkrete Anzahlen zufaellig fehlschlagen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("REST-Schnittstelle")
class StationApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MeasurementRepository measurements;

    /**
     * Vor jedem Test: alles loeschen, dann einen definierten Datenbestand
     * aufbauen. Das Repository ist eine Singleton-Bean und wird vom
     * Spring-Context zwischen den Tests wiederverwendet - ohne {@code clear()}
     * wuerden sich die Daten der Tests gegenseitig beeinflussen, und das
     * Ergebnis haenge von der Ausfuehrungsreihenfolge ab.
     *
     * <p>Fuenf Messwerte fuer ST-001 im Minutenabstand, mit absichtlich
     * gewaehlten Pegeln:
     * <pre>
     *   T0+0   275 cm  NORMAL
     *   T0+1   290 cm  NORMAL
     *   T0+2   315 cm  WARNING    (>= 310)
     *   T0+3   370 cm  CRITICAL   (>= 360)
     *   T0+4   300 cm  NORMAL
     * </pre>
     */
    @BeforeEach
    void setUp() {
        measurements.clear();
        measurements.add("ST-001", TestData.at(0, 275.0));
        measurements.add("ST-001", TestData.at(1, 290.0));
        measurements.add("ST-001", TestData.at(2, 315.0));
        measurements.add("ST-001", TestData.at(3, 370.0));
        measurements.add("ST-001", TestData.at(4, 300.0));
    }

    // =====================================================================
    // 1 + 2: Stationsliste in beiden Formaten
    // =====================================================================

    @Test
    @DisplayName("1. Alle Stationen als JSON")
    void allStationsAsJson() throws Exception {
        mvc.perform(get("/api/v1/stations").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.stations").isArray())
                // Die Angabe verlangt mindestens drei Stationen.
                .andExpect(jsonPath("$.stations.length()").value(4))
                .andExpect(jsonPath("$.stations[0].id").value("ST-001"))
                .andExpect(jsonPath("$.stations[0].river").value("Donau"))
                .andExpect(jsonPath("$.stations[0].location.latitude").exists());
    }

    @Test
    @DisplayName("2. Alle Stationen als XML")
    void allStationsAsXml() throws Exception {
        mvc.perform(get("/api/v1/stations").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                // XPath prueft die Struktur, nicht nur das Vorkommen von Text:
                // <stations> als Wurzel mit <station>-Kindern, ohne doppelte Huelle.
                .andExpect(xpath("/stations/station").nodeCount(4))
                .andExpect(xpath("/stations/station[1]/id").string("ST-001"))
                .andExpect(xpath("/stations/station[1]/location/latitude").exists());
    }

    @Test
    @DisplayName("JSON und XML enthalten dieselben fachlichen Daten")
    void jsonAndXmlAgree() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Donau Wien-Nussdorf"))
                .andExpect(jsonPath("$.warningWaterLevel").value(310.0))
                .andExpect(jsonPath("$.isActive").value(true));

        mvc.perform(get("/api/v1/stations/ST-001").accept(MediaType.APPLICATION_XML))
                .andExpect(xpath("/station/name").string("Donau Wien-Nussdorf"))
                .andExpect(xpath("/station/warningWaterLevel").string("310.0"))
                .andExpect(xpath("/station/isActive").string("true"));
    }

    // =====================================================================
    // 3 + 4: einzelne Station
    // =====================================================================

    @Test
    @DisplayName("3. Existierende Station abrufen")
    void existingStation() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ST-002"))
                .andExpect(jsonPath("$.name").value("Donau Krems"));
    }

    @Test
    @DisplayName("4. Unbekannte Station liefert 404 mit ApiError")
    void unknownStation() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/v1/stations/ST-999"))
                .andExpect(jsonPath("$.message").exists())
                // Punkt 6 der Angabe: keine Stacktraces nach aussen.
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("Fehler kommen auch als XML")
    void errorAsXml() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-999").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotFound())
                .andExpect(xpath("/error/status").string("404"))
                .andExpect(xpath("/error/path").string("/api/v1/stations/ST-999"));
    }

    // =====================================================================
    // 5: aktuellster Messwert
    // =====================================================================

    @Test
    @DisplayName("5. Aktuellsten Messwert abrufen")
    void latestMeasurement() throws Exception {
        // Der zuletzt hinzugefuegte Wert ist der von T0+4 mit 300 cm.
        mvc.perform(get("/api/v1/stations/ST-001/measurements/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value("ST-001"))
                .andExpect(jsonPath("$.waterLevel").value(300.0))
                .andExpect(jsonPath("$.warningLevel").value("NORMAL"))
                .andExpect(jsonPath("$.timestamp").value("2026-05-01T08:04:00Z"));
    }

    @Test
    @DisplayName("Station ohne Messwerte liefert 404")
    void latestWithoutData() throws Exception {
        // ST-004 ist deaktiviert und bekommt vom Simulator nie einen Messwert.
        mvc.perform(get("/api/v1/stations/ST-004/measurements/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =====================================================================
    // 6 + 7: Zeitraum
    // =====================================================================

    @Test
    @DisplayName("6. Filterung nach Zeitraum, Grenzen inklusive")
    void filterByTimeRange() throws Exception {
        // T0+1 bis T0+3 umfasst drei der fuenf Messwerte.
        mvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("from", "2026-05-01T08:01:00Z")
                        .param("to", "2026-05-01T08:03:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements.length()").value(3))
                // Der Wert genau auf der unteren Grenze muss enthalten sein.
                .andExpect(jsonPath("$.measurements[0].timestamp").value("2026-05-01T08:01:00Z"))
                // ... und der auf der oberen ebenso.
                .andExpect(jsonPath("$.measurements[2].timestamp").value("2026-05-01T08:03:00Z"));
    }

    @Test
    @DisplayName("7. Ungueltiger Zeitraum (from nach to) liefert 400")
    void invalidTimeRange() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/measurements")
                        .param("from", "2026-05-01T12:00:00Z")
                        .param("to", "2026-05-01T08:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("liegt nach")));
    }

    @Test
    @DisplayName("Ungueltiges Datumsformat liefert 400")
    void invalidDateFormat() throws Exception {
        mvc.perform(get("/api/v1/stations/ST-001/measurements").param("from", "gestern"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // =====================================================================
    // 8 bis 10: die drei Warnstufen
    // =====================================================================

    @Nested
    @DisplayName("Warnstufen")
    class WarningLevels {

        @Test
        @DisplayName("8. Warnstufe NORMAL")
        void normal() throws Exception {
            // 275, 290 und 300 cm liegen unter dem Warnwert 310.
            mvc.perform(get("/api/v1/stations/ST-001/measurements")
                            .param("warningLevel", "NORMAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.measurements.length()").value(3))
                    .andExpect(jsonPath("$.measurements[0].warningLevel").value("NORMAL"));
        }

        @Test
        @DisplayName("9. Warnstufe WARNING ab dem Warnwert")
        void warning() throws Exception {
            // 315 cm liegt ueber 310 und unter 360.
            mvc.perform(get("/api/v1/stations/ST-001/measurements")
                            .param("warningLevel", "WARNING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.measurements.length()").value(1))
                    .andExpect(jsonPath("$.measurements[0].waterLevel").value(315.0));
        }

        @Test
        @DisplayName("10. Warnstufe CRITICAL ab dem kritischen Wert")
        void critical() throws Exception {
            // 370 cm liegt ueber 360.
            mvc.perform(get("/api/v1/stations/ST-001/measurements")
                            .param("warningLevel", "CRITICAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.measurements.length()").value(1))
                    .andExpect(jsonPath("$.measurements[0].waterLevel").value(370.0));
        }

        @Test
        @DisplayName("Die Schwellwerte gelten inklusive")
        void thresholdsAreInclusive() throws Exception {
            measurements.clear();
            // Genau auf dem Warnwert und genau auf dem kritischen Wert.
            measurements.add("ST-001", TestData.at(0, TestData.ST001_WARNING));
            measurements.add("ST-001", TestData.at(1, TestData.ST001_CRITICAL));

            mvc.perform(get("/api/v1/stations/ST-001/measurements"))
                    .andExpect(jsonPath("$.measurements[0].warningLevel").value("WARNING"))
                    .andExpect(jsonPath("$.measurements[1].warningLevel").value("CRITICAL"));
        }

        @Test
        @DisplayName("Unbekannte Warnstufe liefert 400")
        void unknownLevel() throws Exception {
            mvc.perform(get("/api/v1/stations").param("warningLevel", "HOCH"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("HOCH")));
        }
    }

    // =====================================================================
    // 11: Content Negotiation
    // =====================================================================

    @Test
    @DisplayName("11. Nicht unterstuetztes Format liefert 406")
    void unsupportedFormat() throws Exception {
        mvc.perform(get("/api/v1/stations").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.status").value(406));
    }

    @Test
    @DisplayName("Ohne Accept-Header kommt JSON")
    void defaultsToJson() throws Exception {
        mvc.perform(get("/api/v1/stations"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // =====================================================================
    // 12: Limit
    // =====================================================================

    @Nested
    @DisplayName("Parameter limit")
    class Limit {

        @Test
        @DisplayName("12. Maximales Limit wird abgelehnt")
        void aboveMaximum() throws Exception {
            mvc.perform(get("/api/v1/stations/ST-001/measurements")
                            .param("limit", String.valueOf(MeasurementService.MAX_LIMIT + 1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Genau das Maximum ist erlaubt")
        void exactlyMaximum() throws Exception {
            mvc.perform(get("/api/v1/stations/ST-001/measurements")
                            .param("limit", String.valueOf(MeasurementService.MAX_LIMIT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.measurements.length()").value(5));
        }

        @Test
        @DisplayName("limit = 0 und negative Werte werden abgelehnt")
        void zeroAndNegative() throws Exception {
            mvc.perform(get("/api/v1/stations/ST-001/measurements").param("limit", "0"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/stations/ST-001/measurements").param("limit", "-5"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("limit liefert die neuesten Werte, chronologisch sortiert")
        void returnsNewest() throws Exception {
            mvc.perform(get("/api/v1/stations/ST-001/measurements").param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.measurements.length()").value(2))
                    // T0+3 und T0+4, nicht T0+0 und T0+1.
                    .andExpect(jsonPath("$.measurements[0].timestamp")
                            .value("2026-05-01T08:03:00Z"))
                    .andExpect(jsonPath("$.measurements[1].timestamp")
                            .value("2026-05-01T08:04:00Z"));
        }

        @Test
        @DisplayName("limit ist keine Zahl - 400")
        void notANumber() throws Exception {
            mvc.perform(get("/api/v1/stations/ST-001/measurements").param("limit", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    // Stationsfilter
    // =====================================================================

    @Nested
    @DisplayName("Stationsfilter")
    class StationFilters {

        @Test
        @DisplayName("Filter nach Fluss, Schreibweise egal")
        void byRiver() throws Exception {
            mvc.perform(get("/api/v1/stations").param("river", "donau"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stations.length()").value(2));
        }

        @Test
        @DisplayName("Filter nach isActive")
        void byActive() throws Exception {
            mvc.perform(get("/api/v1/stations").param("isActive", "false"))
                    .andExpect(jsonPath("$.stations.length()").value(1))
                    .andExpect(jsonPath("$.stations[0].id").value("ST-004"));

            mvc.perform(get("/api/v1/stations").param("isActive", "true"))
                    .andExpect(jsonPath("$.stations.length()").value(3));
        }

        @Test
        @DisplayName("Filter nach aktueller Warnstufe")
        void byCurrentWarningLevel() throws Exception {
            measurements.clear();
            measurements.add("ST-001", TestData.at(java.time.Instant.now(), 370.0));

            mvc.perform(get("/api/v1/stations").param("warningLevel", "CRITICAL"))
                    .andExpect(jsonPath("$.stations.length()").value(1))
                    .andExpect(jsonPath("$.stations[0].id").value("ST-001"));

            // Alle uebrigen haben keinen Messwert und gelten damit als UNKNOWN.
            mvc.perform(get("/api/v1/stations").param("warningLevel", "UNKNOWN"))
                    .andExpect(jsonPath("$.stations.length()").value(3));
        }

        @Test
        @DisplayName("Kein Treffer ist 200 mit leerer Liste, nicht 404")
        void noMatchIsEmptyList() throws Exception {
            mvc.perform(get("/api/v1/stations").param("river", "Nil"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stations.length()").value(0));
        }
    }

    // =====================================================================
    // Warnungen
    // =====================================================================

    @Nested
    @DisplayName("Warnungen")
    class Alerts {

        @Test
        @DisplayName("Nur Stationen mit Warnstufe ungleich NORMAL erscheinen")
        void onlyNonNormal() throws Exception {
            measurements.clear();
            measurements.add("ST-001", TestData.at(java.time.Instant.now(), 370.0));

            mvc.perform(get("/api/v1/alerts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alerts.length()").value(1))
                    .andExpect(jsonPath("$.alerts[0].stationId").value("ST-001"))
                    .andExpect(jsonPath("$.alerts[0].warningLevel").value("CRITICAL"));
        }

        @Test
        @DisplayName("Station mit NORMAL erzeugt keine Warnung")
        void normalIsNoAlert() throws Exception {
            measurements.clear();
            measurements.add("ST-001", TestData.at(java.time.Instant.now(), 275.0));

            mvc.perform(get("/api/v1/alerts"))
                    .andExpect(jsonPath("$.alerts.length()").value(0));
        }

        @Test
        @DisplayName("OFFLINE-Station erscheint als UNKNOWN")
        void offlineIsUnknown() throws Exception {
            measurements.clear();
            measurements.add("ST-001", TestData.at(java.time.Instant.now(), 275.0,
                    StationStatus.OFFLINE, WarningLevel.UNKNOWN));

            mvc.perform(get("/api/v1/alerts").param("warningLevel", "UNKNOWN"))
                    .andExpect(jsonPath("$.alerts.length()").value(1))
                    .andExpect(jsonPath("$.alerts[0].stationId").value("ST-001"));
        }

        @Test
        @DisplayName("Warnungen als XML")
        void asXml() throws Exception {
            measurements.clear();
            measurements.add("ST-001", TestData.at(java.time.Instant.now(), 370.0));

            mvc.perform(get("/api/v1/alerts").accept(MediaType.APPLICATION_XML))
                    .andExpect(status().isOk())
                    .andExpect(xpath("/alerts/alert").nodeCount(1))
                    .andExpect(xpath("/alerts/alert[1]/warningLevel").string("CRITICAL"));
        }
    }

    // =====================================================================
    // Statistik
    // =====================================================================

    @Nested
    @DisplayName("Statistik")
    class StatisticsTests {

        @Test
        @DisplayName("Kennzahlen ueber die gesamte Historie")
        void overAllMeasurements() throws Exception {
            // Pegel: 275, 290, 315, 370, 300 -> min 275, max 370, Summe 1550, Mittel 310
            mvc.perform(get("/api/v1/stations/ST-001/statistics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.measurementCount").value(5))
                    .andExpect(jsonPath("$.minWaterLevel").value(275.0))
                    .andExpect(jsonPath("$.maxWaterLevel").value(370.0))
                    .andExpect(jsonPath("$.avgWaterLevel").value(310.0))
                    // WARNING zaehlt CRITICAL mit: 315 und 370.
                    .andExpect(jsonPath("$.warningCount").value(2))
                    .andExpect(jsonPath("$.criticalCount").value(1));
        }

        @Test
        @DisplayName("Statistik beachtet dieselben Filter wie die Historie")
        void respectsFilters() throws Exception {
            mvc.perform(get("/api/v1/stations/ST-001/statistics")
                            .param("from", "2026-05-01T08:02:00Z")
                            .param("to", "2026-05-01T08:03:00Z"))
                    .andExpect(jsonPath("$.measurementCount").value(2))
                    .andExpect(jsonPath("$.minWaterLevel").value(315.0))
                    .andExpect(jsonPath("$.maxWaterLevel").value(370.0));
        }

        @Test
        @DisplayName("Leere Menge ist 200 mit measurementCount 0")
        void emptyIsOk() throws Exception {
            mvc.perform(get("/api/v1/stations/ST-004/statistics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.measurementCount").value(0));
        }

        @Test
        @DisplayName("Unbekannte Station liefert auch hier 404")
        void unknownStation() throws Exception {
            mvc.perform(get("/api/v1/stations/ST-999/statistics"))
                    .andExpect(status().isNotFound());
        }
    }

    // =====================================================================
    // Falsche Pfade und Verben
    // =====================================================================

    @Nested
    @DisplayName("Falsche Pfade und Verben")
    class WrongRequests {

        @Test
        @DisplayName("Unbekannter Pfad liefert 404 und nicht 500")
        void unknownPath() throws Exception {
            mvc.perform(get("/api/v1/gibtesnicht"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Falsches HTTP-Verb liefert 405 mit Allow-Header")
        void wrongMethod() throws Exception {
            mvc.perform(post("/api/v1/stations"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                    .andExpect(jsonPath("$.status").value(405));
        }
    }
}
