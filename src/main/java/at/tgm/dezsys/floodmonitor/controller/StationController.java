package at.tgm.dezsys.floodmonitor.controller;

import at.tgm.dezsys.floodmonitor.model.Measurement;
import at.tgm.dezsys.floodmonitor.model.MeasurementList;
import at.tgm.dezsys.floodmonitor.model.Station;
import at.tgm.dezsys.floodmonitor.model.StationList;
import at.tgm.dezsys.floodmonitor.model.Statistics;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import at.tgm.dezsys.floodmonitor.service.MeasurementService;
import at.tgm.dezsys.floodmonitor.service.StationService;
import at.tgm.dezsys.floodmonitor.service.StatisticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * HTTP-Schnittstelle fuer Messstationen und ihre Messwerte.
 * <p>
 * {@code @RestController} = {@code @Controller} + {@code @ResponseBody}: der
 * Rueckgabewert wird nicht als View-Name interpretiert, sondern vom passenden
 * HttpMessageConverter serialisiert. Welcher Converter das ist, entscheidet der
 * {@code Accept}-Header des Requests - das ist die Content Negotiation.
 * <p>
 * Jede Methode hier besteht aus genau zwei Schritten: Parameter annehmen und an
 * einen Service weitergeben. Kein {@code if}, keine Berechnung, keine
 * Datenhaltung. Die Angabe verlangt das ausdruecklich, und es hat einen
 * praktischen Grund: Logik im Controller laesst sich nur noch ueber HTTP testen.
 * <p>
 * <b>Kein {@code @Validated} an dieser Klasse.</b> Seit Spring Framework 6.1
 * pruefen Controller Constraints wie {@code @Min} an ihren Parametern von sich
 * aus und werfen dabei eine {@code HandlerMethodValidationException} - genau
 * die, die der {@code ApiExceptionHandler} in einen 400 uebersetzt. Mit
 * {@code @Validated} wuerde stattdessen der aeltere AOP-Weg greifen und eine
 * {@code ConstraintViolationException} werfen, fuer die es hier keinen Handler
 * gibt: aus 400 wuerde dann 500.
 */
@RestController
@RequestMapping("/api/v1")
public class StationController {

    private final StationService stations;
    private final MeasurementService measurements;
    private final StatisticsService statistics;

    public StationController(StationService stations,
                            MeasurementService measurements,
                            StatisticsService statistics) {
        this.stations = stations;
        this.measurements = measurements;
        this.statistics = statistics;
    }

    /**
     * GET /api/v1/stations
     * <p>
     * Beispiele:
     * <pre>
     * /api/v1/stations
     * /api/v1/stations?river=Donau
     * /api/v1/stations?isActive=true
     * /api/v1/stations?warningLevel=CRITICAL
     * /api/v1/stations?river=Donau&amp;warningLevel=WARNING
     * </pre>
     * Die Filter sind kombinierbar und alle optional. Ein leeres Ergebnis ist
     * 200 mit leerer Liste, nicht 404: die Sammlung {@code /stations} existiert,
     * sie enthaelt zu diesen Kriterien nur nichts.
     * <p>
     * {@link WarningLevel} als Parametertyp statt {@code String}: Spring
     * konvertiert den Text selbst ins Enum. Bei {@code ?warningLevel=HOCH}
     * entsteht eine {@code MethodArgumentTypeMismatchException}, die der
     * ApiExceptionHandler als 400 beantwortet - ohne eine Zeile Pruefcode hier.
     */
    @GetMapping("/stations")
    public StationList allStations(
            @RequestParam(required = false) String river,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) WarningLevel warningLevel) {
        return new StationList(stations.findAll(river, isActive, warningLevel));
    }

    /** GET /api/v1/stations/ST-001 */
    @GetMapping("/stations/{stationId}")
    public Station oneStation(@PathVariable String stationId) {
        return stations.byId(stationId);
    }

    /** GET /api/v1/stations/ST-001/measurements/latest */
    @GetMapping("/stations/{stationId}/measurements/latest")
    public Measurement latestMeasurement(@PathVariable String stationId) {
        return measurements.latest(stationId);
    }

    /**
     * GET /api/v1/stations/ST-001/measurements
     * <p>
     * Beispiel aus der Angabe:
     * <pre>
     * /api/v1/stations/ST-001/measurements?from=2026-05-01T08:00:00Z&amp;to=2026-05-01T12:00:00Z&amp;limit=100
     * </pre>
     * {@link Instant} als Parametertyp: Spring hat fuer die Typen aus
     * {@code java.time} einen eingebauten Formatter und liest ISO-8601 direkt.
     * Ein kaputtes Datum wird dadurch zum 400 und nicht zu einem Parse-Fehler
     * mitten im Service.
     * <p>
     * Achtung bei Zeitzonen-Offsets: In einer Query steht {@code +} fuer ein
     * Leerzeichen. {@code 2026-05-01T08:00:00+02:00} muss deshalb als
     * {@code 2026-05-01T08:00:00%2B02:00} gesendet werden. Mit {@code Z} (UTC)
     * tritt das Problem nicht auf.
     * <p>
     * {@code @Min}/{@code @Max} an {@code limit} lehnen 0, negative Werte und
     * Ausreisser wie 99999 mit 400 ab - siehe Klassenkommentar zur Frage, warum
     * hier kein {@code @Validated} steht.
     */
    @GetMapping("/stations/{stationId}/measurements")
    public MeasurementList measurementHistory(
            @PathVariable String stationId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false)
            @Min(1) @Max(MeasurementService.MAX_LIMIT) Integer limit,
            @RequestParam(required = false) WarningLevel warningLevel) {
        return new MeasurementList(
                measurements.history(stationId, from, to, limit, warningLevel));
    }

    /**
     * GET /api/v1/stations/ST-001/statistics
     * <p>
     * Dieselben Filter wie bei der Historie, damit sich Tabelle und Kennzahlen
     * im Webclient auf dieselbe Auswahl beziehen.
     */
    @GetMapping("/stations/{stationId}/statistics")
    public Statistics statistics(
            @PathVariable String stationId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false)
            @Min(1) @Max(MeasurementService.MAX_LIMIT) Integer limit,
            @RequestParam(required = false) WarningLevel warningLevel) {
        return statistics.forStation(stationId, from, to, limit, warningLevel);
    }
}
