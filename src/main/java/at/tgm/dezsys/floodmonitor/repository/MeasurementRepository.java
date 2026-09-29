package at.tgm.dezsys.floodmonitor.repository;

import at.tgm.dezsys.floodmonitor.model.Measurement;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Haelt die Messwert-Historie je Station im Arbeitsspeicher.
 * <p>
 * Der Simulator schreibt hier aus einem Scheduler-Thread hinein, waehrend
 * Tomcat-Threads gleichzeitig fuer HTTP-Requests lesen. Deshalb
 * {@link ConcurrentHashMap} fuer die Map und {@link CopyOnWriteArrayList} fuer
 * die Listen - mit {@code HashMap}/{@code ArrayList} gaebe es hier frueher oder
 * spaeter eine {@code ConcurrentModificationException} mitten in einem Request.
 */
@Repository
public class MeasurementRepository {

    /** Obergrenze je Station, damit der Speicher bei Dauerbetrieb nicht waechst. */
    private static final int MAX_HISTORY_PER_STATION = 1000;

    /** stationId -> Messwerte, aeltester zuerst. */
    private final Map<String, List<Measurement>> history = new ConcurrentHashMap<>();

    public void add(String stationId, Measurement measurement) {
        List<Measurement> entries =
                history.computeIfAbsent(stationId, key -> new CopyOnWriteArrayList<>());
        entries.add(measurement);

        // Ringpuffer: aelteste Eintraege verwerfen.
        while (entries.size() > MAX_HISTORY_PER_STATION) {
            entries.removeFirst();
        }
    }

    /** Der zuletzt erzeugte Messwert, oder leer, wenn noch keiner existiert. */
    public Optional<Measurement> latest(String stationId) {
        List<Measurement> entries = history.get(stationId);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(entries.size() - 1));
    }

    /** Die vollstaendige Historie einer Station, aeltester Wert zuerst. */
    public List<Measurement> findByStation(String stationId) {
        return List.copyOf(history.getOrDefault(stationId, List.of()));
    }

    /** Wird von Tests gebraucht, um mit definierten Daten zu starten. */
    public void clear() {
        history.clear();
    }
}
