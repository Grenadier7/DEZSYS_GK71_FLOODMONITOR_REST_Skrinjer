package at.tgm.dezsys.floodmonitor;

import at.tgm.dezsys.floodmonitor.model.Measurement;
import at.tgm.dezsys.floodmonitor.model.StationStatus;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;

import java.time.Instant;

/**
 * Baut Messwerte fuer die Tests.
 *
 * <p>Warum eine eigene Klasse und nicht einfach {@code new Measurement(...)} im
 * Test? Der Record hat neun Komponenten. Ein Aufruf mit neun Werten in einer
 * Zeile sagt beim Lesen nichts darueber, welcher davon der Pegel ist - und
 * genau der ist in fast jedem Test die einzige Groesse, auf die es ankommt.
 * {@link #at(Instant, double)} macht das sichtbar: alles Belanglose bekommt
 * einen festen Wert, der Pegel steht als einziger im Aufruf.
 *
 * <p>Die Zeitstempel kommen immer von aussen. Ein {@code Instant.now()} im
 * Testaufbau wuerde die Zeitraumfilter von der Uhr der Testmaschine abhaengig
 * machen - die Angabe verlangt aber, dass Tests nicht zufaellig fehlschlagen.
 */
final class TestData {

    /** Fester Bezugspunkt. Alle Testzeitpunkte leiten sich hiervon ab. */
    static final Instant T0 = Instant.parse("2026-05-01T08:00:00Z");

    /** Die Schwellwerte von ST-001 aus dem StationRepository. */
    static final double ST001_NORMAL = 280.0;
    static final double ST001_WARNING = 310.0;
    static final double ST001_CRITICAL = 360.0;

    private TestData() {
    }

    /** Ein Messwert fuer ST-001 zum Zeitpunkt {@code T0 + minutes}. */
    static Measurement at(long minutes, double waterLevel) {
        return at(T0.plusSeconds(minutes * 60), waterLevel);
    }

    /**
     * Ein Messwert fuer ST-001 mit dem gegebenen Pegel. Die Warnstufe wird nach
     * denselben Regeln gesetzt wie im Produktivcode, damit die Testdaten in
     * sich stimmig sind.
     */
    static Measurement at(Instant timestamp, double waterLevel) {
        WarningLevel level;
        if (waterLevel >= ST001_CRITICAL) {
            level = WarningLevel.CRITICAL;
        } else if (waterLevel >= ST001_WARNING) {
            level = WarningLevel.WARNING;
        } else {
            level = WarningLevel.NORMAL;
        }

        return new Measurement("ST-001", timestamp, waterLevel,
                waterLevel * 2.5, 0.0, 14.0, 100, StationStatus.ONLINE, level);
    }

    /** Ein Messwert mit frei gewaehltem Status und frei gewaehlter Warnstufe. */
    static Measurement at(Instant timestamp, double waterLevel,
                          StationStatus status, WarningLevel level) {
        return new Measurement("ST-001", timestamp, waterLevel,
                waterLevel * 2.5, 0.0, 14.0, 100, status, level);
    }
}
