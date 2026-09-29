package at.tgm.dezsys.floodmonitor.service;

import at.tgm.dezsys.floodmonitor.exceptions.InvalidRangeException;
import at.tgm.dezsys.floodmonitor.exceptions.MeasurementNotFoundException;
import at.tgm.dezsys.floodmonitor.model.Measurement;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import at.tgm.dezsys.floodmonitor.repository.MeasurementRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Geschaeftslogik rund um Messwerte.
 * <p>
 * Prueft zuerst ueber den {@link StationService}, ob die Station ueberhaupt
 * existiert. Sonst wuerde eine unbekannte Stations-ID eine leere Liste liefern
 * statt eines 404 - fachlich sind das zwei verschiedene Aussagen.
 */
@Service
public class MeasurementService {

    /**
     * Groesster erlaubter Wert fuer {@code ?limit=}.
     * <p>
     * {@code public static final int} und nicht nur eine Zahl im Controller:
     * eine Konstante ist ein Compile-Time-Constant und darf deshalb direkt in
     * der Annotation {@code @Max(...)} stehen. So steht die Obergrenze an genau
     * einer Stelle und kann nicht zwischen Validierung und Doku auseinanderlaufen.
     * <p>
     * Der Wert entspricht {@code MeasurementRepository.MAX_HISTORY_PER_STATION} -
     * mehr Werte gibt es pro Station gar nicht.
     */
    public static final int MAX_LIMIT = 1000;

    private final MeasurementRepository measurements;
    private final StationService stations;

    public MeasurementService(MeasurementRepository measurements, StationService stations) {
        this.measurements = measurements;
        this.stations = stations;
    }

    /** Station unbekannt -> 404. Station bekannt, aber noch kein Messwert -> ebenfalls 404. */
    public Measurement latest(String stationId) {
        stations.byId(stationId);

        return measurements.latest(stationId)
                .orElseThrow(() -> new MeasurementNotFoundException(stationId));
    }

    /** Vollstaendige Historie, ohne Filter. */
    public List<Measurement> history(String stationId) {
        return history(stationId, null, null, null, null);
    }

    /**
     * Historie einer Station, gefiltert. Jeder Filter darf {@code null} sein.
     * <p>
     * Reihenfolge der Schritte ist nicht beliebig:
     * <ol>
     *   <li>Existiert die Station? Sonst 404 - noch bevor irgendetwas gefiltert wird.</li>
     *   <li>Ist der Zeitraum plausibel? Sonst 400. Eine leere Liste wegen
     *       {@code from > to} waere eine falsche Antwort: der Server tut so, als
     *       haette er die Frage verstanden.</li>
     *   <li>Filtern.</li>
     *   <li>Erst zum Schluss kuerzen - {@code limit} begrenzt das Ergebnis,
     *       nicht die Eingabemenge.</li>
     * </ol>
     *
     * @param from  fruehester Zeitpunkt, inklusive
     * @param to    spaetester Zeitpunkt, inklusive
     * @param limit maximale Anzahl; geliefert werden die <em>neuesten</em> n Werte
     * @throws InvalidRangeException wenn {@code from} nach {@code to} liegt
     */
    public List<Measurement> history(String stationId, Instant from, Instant to,
                                     Integer limit, WarningLevel warningLevel) {
        stations.byId(stationId);

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRangeException(
                    "Parameter 'from' (%s) liegt nach 'to' (%s)".formatted(from, to));
        }

        // !isBefore(from) heisst ">= from", !isAfter(to) heisst "<= to".
        // isAfter/isBefore allein waeren exklusiv und wuerden einen Messwert,
        // der genau auf der Grenze liegt, verschlucken.
        List<Measurement> result = measurements.findByStation(stationId).stream()
                .filter(m -> from == null || !m.timestamp().isBefore(from))
                .filter(m -> to == null || !m.timestamp().isAfter(to))
                .filter(m -> warningLevel == null || m.warningLevel() == warningLevel)
                .toList();

        if (limit != null && result.size() > limit) {
            // Vom Ende her schneiden: bei "?limit=10" will ein Monitoring-Client
            // die letzten zehn Messungen sehen, nicht die zehn aeltesten.
            // Die chronologische Reihenfolge bleibt dabei erhalten.
            return List.copyOf(result.subList(result.size() - limit, result.size()));
        }
        return result;
    }
}
