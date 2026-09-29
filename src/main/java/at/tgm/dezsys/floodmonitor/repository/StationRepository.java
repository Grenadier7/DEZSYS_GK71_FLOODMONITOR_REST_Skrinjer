package at.tgm.dezsys.floodmonitor.repository;

import at.tgm.dezsys.floodmonitor.model.Location;
import at.tgm.dezsys.floodmonitor.model.Station;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Haelt die Messstationen im Arbeitsspeicher.
 * <p>
 * {@code @Repository} macht die Klasse zur Bean. Spring erzeugt davon genau ein
 * Objekt (Singleton), das sich Controller und Simulator teilen - genau deshalb
 * sehen beide dieselben Daten.
 * <p>
 * Weil der Simulator spaeter aus einem Hintergrund-Thread schreibt, waehrend
 * Tomcat-Threads gleichzeitig lesen, ist die {@link ConcurrentHashMap} Pflicht.
 * Eine normale {@code HashMap} wuerde hier sporadisch eine
 * {@code ConcurrentModificationException} mitten in einem Request ausloesen.
 */
@Repository
public class StationRepository {

    private final Map<String, Station> stations = new ConcurrentHashMap<>();

    /**
     * Startdaten. Der Konstruktor einer Bean laeuft beim Hochfahren des
     * Application Context, also bevor der erste Request ankommt.
     * (Alternative waere eine eigene Methode mit {@code @PostConstruct}.)
     * <p>
     * Drei aktive Stationen sind das Minimum aus der Angabe. ST-004 kommt
     * dazu, damit {@code isActive == false} nicht nur ein Feld im Datenmodell
     * ist, sondern ein Fall, den man an der API sehen kann: die Station
     * erscheint unter {@code /stations?isActive=false}, bekommt vom Simulator
     * nie einen Messwert, liefert bei {@code /measurements/latest} einen 404
     * und zaehlt bei {@code ?warningLevel=UNKNOWN} mit.
     */
    public StationRepository() {
        save(new Station("ST-001", "Donau Wien-Nussdorf", "Donau",
                new Location(48.2540, 16.3660), 280.0, 310.0, 360.0, true));
        save(new Station("ST-002", "Donau Krems", "Donau",
                new Location(48.4100, 15.6000), 265.0, 300.0, 350.0, true));
        save(new Station("ST-003", "Traisen St. Poelten", "Traisen",
                new Location(48.2000, 15.6300), 120.0, 150.0, 190.0, true));
        save(new Station("ST-004", "Ybbs Amstetten (ausser Betrieb)", "Ybbs",
                new Location(48.1200, 14.8700), 95.0, 130.0, 170.0, false));
    }

    public void save(Station station) {
        stations.put(station.id(), station);
    }

    /**
     * {@link Optional} statt {@code null}: der Aufrufer muss sich entscheiden,
     * was bei einer unbekannten ID passieren soll. In Schritt 20 wird daraus
     * ein sauberer 404 statt einer NullPointerException.
     */
    public Optional<Station> findById(String id) {
        return Optional.ofNullable(stations.get(id));
    }

    /**
     * Nach ID sortiert, damit die Reihenfolge stabil ist. Eine ConcurrentHashMap
     * garantiert keine Reihenfolge - ohne Sortierung wuerden spaetere Tests
     * gelegentlich grundlos fehlschlagen.
     */
    public List<Station> findAll() {
        return stations.values().stream()
                .sorted(Comparator.comparing(Station::id))
                .toList();
    }

    public boolean deleteById(String id) {
        return stations.remove(id) != null;
    }
}
