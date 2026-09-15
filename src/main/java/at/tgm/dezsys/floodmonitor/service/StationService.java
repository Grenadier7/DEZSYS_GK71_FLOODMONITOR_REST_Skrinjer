package at.tgm.dezsys.floodmonitor.service;

import at.tgm.dezsys.floodmonitor.model.Station;
import at.tgm.dezsys.floodmonitor.repository.StationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Geschaeftslogik rund um Messstationen.
 * <p>
 * Die Schicht sieht jetzt noch fast leer aus, weil sie nur durchreicht. Sie
 * gehoert trotzdem hierher: in Schritt 21 kommen die Filter dazu, und die
 * Angabe verlangt ausdruecklich, dass im Controller keine Geschaeftslogik steht.
 * <p>
 * Dependency Injection: Spring sieht den einen Konstruktor, sucht eine Bean vom
 * Typ {@link StationRepository} und uebergibt sie. Nirgends steht ein {@code new}.
 */
@Service
public class StationService {

    private final StationRepository repository;

    public StationService(StationRepository repository) {
        this.repository = repository;
    }

    public List<Station> findAll() {
        return repository.findAll();
    }

    /**
     * TODO Schritt 20: Die NoSuchElementException durch eine eigene
     * StationNotFoundException ersetzen, die der @RestControllerAdvice
     * in einen 404 mit ApiError-Body uebersetzt. Aktuell gaebe es hier
     * noch einen 500er.
     */
    public Station byId(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "Station " + id + " wurde nicht gefunden"));
    }
}
