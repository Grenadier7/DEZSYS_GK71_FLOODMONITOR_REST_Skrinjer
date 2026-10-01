package at.tgm.dezsys.floodmonitor;

import at.tgm.dezsys.floodmonitor.model.Location;
import at.tgm.dezsys.floodmonitor.model.Station;
import at.tgm.dezsys.floodmonitor.model.StationStatus;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import at.tgm.dezsys.floodmonitor.service.WarningLevelCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reiner Unit-Test der Warnstufen-Berechnung (Angabe Punkt 3).
 *
 * <p>Kein {@code @SpringBootTest}, kein Context, keine HTTP-Schicht: die Klasse
 * wird mit {@code new} erzeugt. Das ist der praktische Gewinn daraus, dass die
 * Berechnung in einer eigenen Klasse ohne Spring-Abhaengigkeiten steckt - der
 * Test laeuft in Millisekunden statt in Sekunden und schlaegt bei einem Fehler
 * punktgenau an, statt irgendwo in einem Request.
 *
 * <p>{@link ParameterizedTest} statt fuenf fast gleicher Methoden: die
 * Tabelle in {@link CsvSource} ist zugleich die lesbarste Dokumentation der
 * Regel - Schwellwert, Eingabe, Erwartung in je einer Zeile.
 */
@DisplayName("Warnstufen-Berechnung")
class WarningLevelCalculatorTest {

    /** Normalpegel 280, Warnwert 310, kritisch 360 - wie ST-001. */
    private static final Station STATION = new Station(
            "ST-001", "Teststation", "Donau", new Location(48.0, 16.0),
            280.0, 310.0, 360.0, true);

    private final WarningLevelCalculator calculator =
            new WarningLevelCalculator(Duration.ofMinutes(5));

    @ParameterizedTest(name = "{0} cm -> {1}")
    @DisplayName("Pegel bestimmt die Warnstufe, Grenzen inklusive")
    @CsvSource({
            "  0.0, NORMAL",     // Untergrenze
            "279.9, NORMAL",     // knapp unter Normalpegel
            "309.9, NORMAL",     // knapp unter dem Warnwert
            "310.0, WARNING",    // genau auf dem Warnwert -> schon WARNING
            "359.9, WARNING",    // knapp unter dem kritischen Wert
            "360.0, CRITICAL",   // genau auf dem kritischen Wert -> CRITICAL
            "999.0, CRITICAL"    // weit darueber
    })
    void levelFollowsWaterLevel(double waterLevel, WarningLevel expected) {
        assertEquals(expected,
                calculator.calculate(STATION, waterLevel, StationStatus.ONLINE));
    }

    @Test
    @DisplayName("OFFLINE-Station ist immer UNKNOWN, egal wie hoch der Pegel ist")
    void offlineIsUnknown() {
        assertEquals(WarningLevel.UNKNOWN,
                calculator.calculate(STATION, 999.0, StationStatus.OFFLINE));
    }

    @Test
    @DisplayName("MAINTENANCE liefert weiterhin eine echte Warnstufe")
    void maintenanceStillMeasures() {
        // Eine Station mit schwachem Akku misst noch - ihre Werte gelten.
        assertEquals(WarningLevel.CRITICAL,
                calculator.calculate(STATION, 400.0, StationStatus.MAINTENANCE));
    }

    @Test
    @DisplayName("Ohne Station oder ohne Status: UNKNOWN statt Absturz")
    void missingInputIsUnknown() {
        assertEquals(WarningLevel.UNKNOWN,
                calculator.calculate(null, 400.0, StationStatus.ONLINE));
        assertEquals(WarningLevel.UNKNOWN,
                calculator.calculate(STATION, 400.0, null));
    }

    @Test
    @DisplayName("Fehlende Messung ist UNKNOWN")
    void noMeasurementIsUnknown() {
        assertEquals(WarningLevel.UNKNOWN, calculator.effective(null));
    }

    @Test
    @DisplayName("Frische Messung behaelt ihre gespeicherte Warnstufe")
    void freshMeasurementKeepsLevel() {
        assertEquals(WarningLevel.CRITICAL,
                calculator.effective(TestData.at(Instant.now(), 370.0)));
    }

    @Test
    @DisplayName("Veraltete Messung wird zu UNKNOWN")
    void staleMeasurementIsUnknown() {
        // Aelter als die konfigurierten fuenf Minuten.
        Instant longAgo = Instant.now().minus(Duration.ofMinutes(10));

        assertEquals(WarningLevel.CRITICAL, TestData.at(longAgo, 370.0).warningLevel(),
                "gespeicherte Stufe bleibt unveraendert");
        assertEquals(WarningLevel.UNKNOWN, calculator.effective(TestData.at(longAgo, 370.0)),
                "die aktuell gueltige Stufe ist UNKNOWN");
    }
}
