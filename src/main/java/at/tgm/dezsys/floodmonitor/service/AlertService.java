package at.tgm.dezsys.floodmonitor.service;

import at.tgm.dezsys.floodmonitor.model.Alert;
import at.tgm.dezsys.floodmonitor.model.Station;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import at.tgm.dezsys.floodmonitor.repository.MeasurementRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Stellt die aktuellen Warnungen zusammen.
 * <p>
 * <b>Was gilt als Warnung?</b> Der letzte Messwert einer Station, dessen
 * Warnstufe nicht {@code NORMAL} ist. {@code UNKNOWN} ist bewusst enthalten:
 * eine Station, die nicht mehr meldet, ist fuer ein Fruehwarnsystem kein
 * Nicht-Ereignis, sondern eine Luecke in der Ueberwachung - genau das, wovon
 * die Leitstelle erfahren muss.
 * <p>
 * Stationen ohne jeden Messwert (deaktiviert, oder gerade erst angelegt)
 * erscheinen nicht: es gibt keinen Zeitpunkt und keinen Pegel, ueber den sich
 * etwas melden liesse.
 * <p>
 * Greift auf das {@link MeasurementRepository} zu und nicht auf den
 * {@link MeasurementService}: dessen {@code latest()} wirft bei fehlendem
 * Messwert eine Exception (richtig fuer {@code /measurements/latest}, wo das ein
 * 404 ist), hier soll eine Station ohne Daten aber einfach uebersprungen werden.
 */
@Service
public class AlertService {

    private final StationService stations;
    private final MeasurementRepository measurements;
    private final WarningLevelCalculator warningLevels;

    public AlertService(StationService stations,
                        MeasurementRepository measurements,
                        WarningLevelCalculator warningLevels) {
        this.stations = stations;
        this.measurements = measurements;
        this.warningLevels = warningLevels;
    }

    /**
     * Aktuelle Warnungen, absteigend nach Dringlichkeit sortiert.
     *
     * @param warningLevel nur diese Stufe; {@code null} = alle ausser NORMAL.
     *                     {@code NORMAL} liefert bewusst eine leere Liste und
     *                     keinen Fehler - die Frage ist gueltig, die Antwort
     *                     lautet "keine".
     * @param river        nur Stationen an diesem Fluss, Schreibweise egal
     */
    public List<Alert> findAlerts(WarningLevel warningLevel, String river) {
        return stations.findAll().stream()
                .filter(station -> river == null || river.equalsIgnoreCase(station.river()))
                .map(this::toAlert)
                // flatMap(Optional::stream) filtert die leeren Optionals heraus und
                // packt die vorhandenen aus - in einem Schritt, ohne isPresent().
                .flatMap(Optional::stream)
                .filter(alert -> alert.warningLevel() != WarningLevel.NORMAL)
                .filter(alert -> warningLevel == null || alert.warningLevel() == warningLevel)
                .sorted(Comparator.comparingInt(AlertService::severity).reversed()
                        .thenComparing(Alert::stationId))
                .toList();
    }

    /**
     * Station plus letzter Messwert zu einer Warnung zusammensetzen.
     * {@link Optional#empty()}, wenn die Station noch nie gemeldet hat.
     */
    private Optional<Alert> toAlert(Station station) {
        return measurements.latest(station.id()).map(measurement -> new Alert(
                station.id(),
                station.name(),
                station.river(),
                warningLevels.effective(measurement),
                measurement.waterLevel(),
                station.warningWaterLevel(),
                station.criticalWaterLevel(),
                measurement.timestamp()));
    }

    /**
     * Dringlichkeit als Zahl, damit sich danach sortieren laesst.
     * <p>
     * Die Reihenfolge im Enum ({@code NORMAL, WARNING, CRITICAL, UNKNOWN}) taugt
     * dafuer nicht: {@code ordinal()} wuerde UNKNOWN als dringendste Stufe
     * einsortieren. Die Rangfolge gehoert deshalb hierher und nicht ins Enum -
     * sie ist eine Entscheidung dieser Ansicht, keine Eigenschaft der Warnstufe.
     */
    private static int severity(Alert alert) {
        return switch (alert.warningLevel()) {
            case CRITICAL -> 3;
            case WARNING -> 2;
            case UNKNOWN -> 1;
            case NORMAL -> 0;
        };
    }
}
