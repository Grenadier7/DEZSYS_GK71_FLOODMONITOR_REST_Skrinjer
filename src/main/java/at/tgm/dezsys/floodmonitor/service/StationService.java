package at.tgm.dezsys.floodmonitor.service;

import at.tgm.dezsys.floodmonitor.exceptions.StationNotFoundException;
import at.tgm.dezsys.floodmonitor.model.Station;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import at.tgm.dezsys.floodmonitor.repository.MeasurementRepository;
import at.tgm.dezsys.floodmonitor.repository.StationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Geschaeftslogik rund um Messstationen.
 * <p>
 * Dependency Injection: Spring sieht den einen Konstruktor, sucht Beans der
 * passenden Typen und uebergibt sie. Nirgends steht ein {@code new}.
 * <p>
 * <b>Warum {@link MeasurementRepository} und nicht {@link MeasurementService}?</b>
 * Der {@code MeasurementService} haengt bereits an dieser Klasse (er braucht
 * {@link #byId(String)} fuer die 404-Pruefung). Eine Abhaengigkeit zurueck waere
 * ein Zyklus, den Spring beim Start mit einer
 * {@code BeanCurrentlyInCreationException} ablehnt. Auf das Repository
 * darf diese Schicht dagegen direkt zugreifen - beide sind Services bzw. tiefer.
 */
@Service
public class StationService {

    private final StationRepository repository;
    private final MeasurementRepository measurements;
    private final WarningLevelCalculator warningLevels;

    public StationService(StationRepository repository,
                         MeasurementRepository measurements,
                         WarningLevelCalculator warningLevels) {
        this.repository = repository;
        this.measurements = measurements;
        this.warningLevels = warningLevels;
    }

    /** Alle Stationen, ohne Filter. */
    public List<Station> findAll() {
        return repository.findAll();
    }

    /**
     * Alle Stationen, gefiltert. Jeder Parameter darf {@code null} sein und
     * bedeutet dann "nicht filtern".
     * <p>
     * Das Muster {@code filter(x -> param == null || bedingung)} haelt die
     * Methode frei von If-Kaskaden: bei acht moeglichen Kombinationen aus drei
     * Filtern gibt es hier trotzdem nur drei Zeilen. Die Reihenfolge der Filter
     * ist absichtlich billig-zuerst - {@code warningLevel} kostet je Station
     * einen Blick in die Messwerte und laeuft deshalb zuletzt.
     *
     * @param river        Flussname, Gross-/Kleinschreibung egal
     * @param isActive     {@code Boolean} statt {@code boolean}, weil nur ein
     *                     Objekttyp {@code null} sein kann - "nicht angegeben"
     *                     und "false" sind verschiedene Aussagen
     * @param warningLevel aktuelle Warnstufe, siehe {@link #currentWarningLevel(String)}
     */
    public List<Station> findAll(String river, Boolean isActive, WarningLevel warningLevel) {
        return repository.findAll().stream()
                .filter(station -> river == null || river.equalsIgnoreCase(station.river()))
                .filter(station -> isActive == null || isActive.equals(station.isActive()))
                .filter(station -> warningLevel == null
                        || currentWarningLevel(station.id()) == warningLevel)
                .toList();
    }

    /**
     * Unbekannte ID wirft eine {@link StationNotFoundException}, die der
     * ApiExceptionHandler in einen 404 mit ApiError-Body uebersetzt.
     */
    public Station byId(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new StationNotFoundException(id));
    }

    /**
     * Die Warnstufe, die fuer diese Station gerade gilt.
     * <p>
     * Die Warnstufe haengt nicht an der Station, sondern an ihrem letzten
     * Messwert - eine Station "ist" nicht kritisch, sie "meldet" gerade einen
     * kritischen Pegel. Ohne Messwert (neue oder deaktivierte Station) ist die
     * Antwort {@code UNKNOWN}, nicht {@code NORMAL}.
     */
    public WarningLevel currentWarningLevel(String stationId) {
        return warningLevels.effective(measurements.latest(stationId).orElse(null));
    }
}
