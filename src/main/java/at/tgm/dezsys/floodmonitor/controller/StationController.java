package at.tgm.dezsys.floodmonitor.controller;

import at.tgm.dezsys.floodmonitor.config.ApiErrorResponses;
import at.tgm.dezsys.floodmonitor.exceptions.ApiError;
import at.tgm.dezsys.floodmonitor.model.Measurement;
import at.tgm.dezsys.floodmonitor.model.MeasurementList;
import at.tgm.dezsys.floodmonitor.model.Station;
import at.tgm.dezsys.floodmonitor.model.StationList;
import at.tgm.dezsys.floodmonitor.model.Statistics;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import at.tgm.dezsys.floodmonitor.service.MeasurementService;
import at.tgm.dezsys.floodmonitor.service.StationService;
import at.tgm.dezsys.floodmonitor.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
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
 * {@code produces} an jedem Mapping nennt die beiden unterstuetzten Formate
 * ausdruecklich. Das hat zwei Wirkungen: die OpenAPI-Dokumentation zeigt
 * {@code application/json} und {@code application/xml} statt eines unbestimmten
 * {@code *&#47;*}, und Spring lehnt jedes andere Wunschformat bereits beim
 * Routing mit 406 ab.
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
@Tag(name = "Messstationen",
     description = "Stationen, ihre Messwerte und statistische Auswertungen")
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
    @GetMapping(value = "/stations",
                produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(summary = "Alle Messstationen abrufen",
               description = """
                       Liefert alle Messstationen. Die drei Filter sind optional \
                       und beliebig kombinierbar. Passt keine Station zu den \
                       Kriterien, ist die Antwort 200 mit einer leeren Liste - \
                       kein 404, denn die Sammlung selbst existiert.""")
    @ApiResponse(responseCode = "200", description = "Liste der Stationen")
    @ApiErrorResponses
    public StationList allStations(

            @Parameter(description = "Flussname. Gross- und Kleinschreibung wird ignoriert.",
                       example = "Donau")
            @RequestParam(required = false) String river,

            @Parameter(description = """
                    Nur aktive (true) oder nur deaktivierte (false) Stationen. \
                    Weggelassen bedeutet: beide.""",
                       example = "true")
            @RequestParam(required = false) Boolean isActive,

            @Parameter(description = """
                    Aktuelle Warnstufe, berechnet aus dem letzten Messwert der \
                    Station. Stationen ohne aktuellen Messwert gelten als UNKNOWN.""",
                       example = "CRITICAL")
            @RequestParam(required = false) WarningLevel warningLevel) {

        return new StationList(stations.findAll(river, isActive, warningLevel));
    }

    /** GET /api/v1/stations/ST-001 */
    @GetMapping(value = "/stations/{stationId}",
                produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(summary = "Einzelne Messstation abrufen",
               description = "Liefert die Stammdaten einer Station inklusive Position "
                           + "und der drei Pegel-Schwellwerte.")
    @ApiResponse(responseCode = "200", description = "Die Station")
    @ApiResponse(responseCode = "404", description = "Station existiert nicht",
            content = {
                @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ApiError.class)),
                @Content(mediaType = "application/xml",
                        schema = @Schema(implementation = ApiError.class))})
    @ApiErrorResponses
    public Station oneStation(
            @Parameter(description = "Technische ID der Station", example = "ST-001")
            @PathVariable String stationId) {
        return stations.byId(stationId);
    }

    /** GET /api/v1/stations/ST-001/measurements/latest */
    @GetMapping(value = "/stations/{stationId}/measurements/latest",
                produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(summary = "Aktuellsten Messwert abrufen",
               description = """
                       Liefert die zuletzt erzeugte Messung der Station. \
                       Die enthaltene Warnstufe ist der beim Erzeugen \
                       serverseitig berechnete Wert.""")
    @ApiResponse(responseCode = "200", description = "Der aktuellste Messwert")
    @ApiResponse(responseCode = "404",
            description = """
                    Station existiert nicht, oder die Station hat noch nie \
                    gemeldet (zum Beispiel weil sie deaktiviert ist).""",
            content = {
                @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ApiError.class)),
                @Content(mediaType = "application/xml",
                        schema = @Schema(implementation = ApiError.class))})
    @ApiErrorResponses
    public Measurement latestMeasurement(
            @Parameter(description = "Technische ID der Station", example = "ST-001")
            @PathVariable String stationId) {
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
    @GetMapping(value = "/stations/{stationId}/measurements",
                produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(summary = "Historische Messwerte abrufen",
               description = """
                       Liefert die gespeicherte Messwert-Historie einer Station, \
                       aeltester Wert zuerst. Alle vier Filter sind optional und \
                       kombinierbar. Die Zeitgrenzen sind inklusive. \
                       Gespeichert werden maximal 1000 Messwerte pro Station.""")
    @ApiResponse(responseCode = "200", description = "Liste der Messwerte")
    @ApiResponse(responseCode = "404", description = "Station existiert nicht",
            content = {
                @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ApiError.class)),
                @Content(mediaType = "application/xml",
                        schema = @Schema(implementation = ApiError.class))})
    @ApiErrorResponses
    public MeasurementList measurementHistory(

            @Parameter(description = "Technische ID der Station", example = "ST-001")
            @PathVariable String stationId,

            @Parameter(description = """
                    Fruehester Zeitpunkt, inklusive. ISO-8601 in UTC. \
                    Hinweis: ein Offset wie +02:00 muss als %2B02:00 kodiert \
                    werden, da + in einer Query ein Leerzeichen bedeutet.""",
                       example = "2026-05-01T08:00:00Z")
            @RequestParam(required = false) Instant from,

            @Parameter(description = "Spaetester Zeitpunkt, inklusive. ISO-8601 in UTC.",
                       example = "2026-05-01T12:00:00Z")
            @RequestParam(required = false) Instant to,

            @Parameter(description = """
                    Maximale Anzahl der Messwerte. Geliefert werden die \
                    *neuesten* n Werte, weiterhin chronologisch sortiert. \
                    Erlaubt ist 1 bis 1000.""",
                       example = "100")
            @RequestParam(required = false)
            @Min(1) @Max(MeasurementService.MAX_LIMIT) Integer limit,

            @Parameter(description = "Nur Messwerte mit dieser Warnstufe",
                       example = "WARNING")
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
    @GetMapping(value = "/stations/{stationId}/statistics",
                produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(summary = "Statistische Auswertung abrufen",
               description = """
                       Kennzahlen ueber die Messwerte einer Station: niedrigster, \
                       hoechster und durchschnittlicher Wasserstand, \
                       durchschnittliche Durchflussmenge, Summe des Niederschlags, \
                       Anzahl der Messungen sowie Anzahl der Warnungen und der \
                       kritischen Warnungen.

                       Die Filter sind dieselben wie bei den historischen \
                       Messwerten und wirken auf dieselbe Datenmenge.

                       Enthaelt der gewaehlte Zeitraum keine Messwerte, ist die \
                       Antwort 200 mit `measurementCount = 0`; die uebrigen Zahlen \
                       sind dann 0 und ohne Aussage.""")
    @ApiResponse(responseCode = "200", description = "Die Auswertung")
    @ApiResponse(responseCode = "404", description = "Station existiert nicht",
            content = {
                @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ApiError.class)),
                @Content(mediaType = "application/xml",
                        schema = @Schema(implementation = ApiError.class))})
    @ApiErrorResponses
    public Statistics statistics(

            @Parameter(description = "Technische ID der Station", example = "ST-001")
            @PathVariable String stationId,

            @Parameter(description = "Fruehester Zeitpunkt, inklusive. ISO-8601 in UTC.",
                       example = "2026-05-01T08:00:00Z")
            @RequestParam(required = false) Instant from,

            @Parameter(description = "Spaetester Zeitpunkt, inklusive. ISO-8601 in UTC.",
                       example = "2026-05-01T12:00:00Z")
            @RequestParam(required = false) Instant to,

            @Parameter(description = "Auswertung nur ueber die neuesten n Messwerte (1 bis 1000)",
                       example = "100")
            @RequestParam(required = false)
            @Min(1) @Max(MeasurementService.MAX_LIMIT) Integer limit,

            @Parameter(description = "Auswertung nur ueber Messwerte dieser Warnstufe",
                       example = "CRITICAL")
            @RequestParam(required = false) WarningLevel warningLevel) {

        return statistics.forStation(stationId, from, to, limit, warningLevel);
    }
}
