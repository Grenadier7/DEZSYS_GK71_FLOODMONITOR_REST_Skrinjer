package at.tgm.dezsys.floodmonitor.service;

import at.tgm.dezsys.floodmonitor.model.Measurement;
import at.tgm.dezsys.floodmonitor.model.Statistics;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.DoubleSummaryStatistics;
import java.util.List;

/**
 * Berechnet die statistische Auswertung aus Punkt 4 der Angabe.
 * <p>
 * Baut auf dem {@link MeasurementService} auf und benutzt genau dessen
 * Filterlogik. Das ist der Grund, warum die Filter dort und nicht im Controller
 * stehen: Statistik und Historie liefern damit garantiert Zahlen zur selben
 * Datenmenge. Waeren die Filter im Controller dupliziert, koennten die
 * Antworten von {@code /measurements?from=X} und {@code /statistics?from=X}
 * auseinanderlaufen, sobald einer der beiden angepasst wird.
 */
@Service
public class StatisticsService {

    private final MeasurementService measurements;

    public StatisticsService(MeasurementService measurements) {
        this.measurements = measurements;
    }

    /**
     * Auswertung fuer eine Station. Filter wie bei der Historie.
     * <p>
     * Leere Datenmenge ist kein Fehler: der Zeitraum kann gueltig sein und
     * trotzdem keine Messwerte enthalten. Die Antwort ist dann 200 mit
     * {@code measurementCount = 0} - ein 404 wuerde behaupten, die Station
     * existiere nicht.
     */
    public Statistics forStation(String stationId, Instant from, Instant to,
                                 Integer limit, WarningLevel warningLevel) {
        List<Measurement> data =
                measurements.history(stationId, from, to, limit, warningLevel);

        if (data.isEmpty()) {
            return new Statistics(stationId, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        // DoubleSummaryStatistics sammelt min, max, sum, count und average in
        // einem einzigen Durchlauf. Von Hand waeren das vier Schleifen oder vier
        // Streams ueber dieselbe Liste.
        DoubleSummaryStatistics level = data.stream()
                .mapToDouble(Measurement::waterLevel).summaryStatistics();
        DoubleSummaryStatistics flow = data.stream()
                .mapToDouble(Measurement::flowRate).summaryStatistics();
        double rainfall = data.stream()
                .mapToDouble(Measurement::rainfall).sum();

        // WARNING zaehlt CRITICAL mit: "Anzahl der Warnungen" meint alle
        // Messwerte, bei denen gewarnt wurde. criticalCount ist davon die
        // Teilmenge der schwersten Faelle.
        int warningCount = (int) data.stream()
                .filter(m -> m.warningLevel() == WarningLevel.WARNING
                        || m.warningLevel() == WarningLevel.CRITICAL)
                .count();
        int criticalCount = (int) data.stream()
                .filter(m -> m.warningLevel() == WarningLevel.CRITICAL)
                .count();

        return new Statistics(
                stationId,
                data.size(),
                round(level.getMin()),
                round(level.getMax()),
                round(level.getAverage()),
                round(flow.getAverage()),
                round(rainfall),
                warningCount,
                criticalCount);
    }

    /** Eine Nachkommastelle - Mittelwerte haetten sonst 15 Stellen Scheingenauigkeit. */
    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
