package at.tgm.dezsys.floodmonitor.controller;

import at.tgm.dezsys.floodmonitor.config.ApiErrorResponses;
import at.tgm.dezsys.floodmonitor.model.AlertList;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import at.tgm.dezsys.floodmonitor.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP-Schnittstelle fuer die aktuellen Warnungen.
 * <p>
 * Eigener Controller und nicht eine weitere Methode im
 * {@link StationController}: {@code /alerts} ist in der Angabe eine eigene
 * Ressource auf oberster Ebene, keine Unterressource einer Station. Ein
 * Controller pro Ressource haelt die Klassen klein und macht die URL-Struktur
 * schon an der Paketuebersicht ablesbar.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Warnungen",
     description = "Aktuelle Hochwasserwarnungen ueber alle Stationen hinweg")
public class AlertController {

    private final AlertService alerts;

    public AlertController(AlertService alerts) {
        this.alerts = alerts;
    }

    /**
     * GET /api/v1/alerts
     * <p>
     * Beispiele:
     * <pre>
     * /api/v1/alerts
     * /api/v1/alerts?warningLevel=CRITICAL
     * /api/v1/alerts?river=Donau
     * </pre>
     */
    @GetMapping(value = "/alerts",
                produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(summary = "Aktuelle Warnungen abrufen",
               description = """
                       Liefert fuer jede Station, deren letzter Messwert eine \
                       Warnstufe ungleich NORMAL hat, einen Warneintrag. \
                       Sortiert nach Dringlichkeit: CRITICAL, dann WARNING, \
                       dann UNKNOWN.

                       UNKNOWN ist absichtlich enthalten: eine Station, die nicht \
                       mehr meldet, ist fuer ein Fruehwarnsystem kein \
                       Nicht-Ereignis, sondern eine Luecke in der Ueberwachung.

                       Stationen, die noch nie einen Messwert geliefert haben, \
                       erscheinen nicht - zu ihnen gibt es weder Pegel noch \
                       Zeitpunkt, ueber den sich etwas melden liesse.

                       `?warningLevel=NORMAL` liefert folgerichtig eine leere \
                       Liste mit Status 200 und keinen Fehler: die Frage ist \
                       gueltig, die Antwort lautet \\"keine\\".""")
    @ApiResponse(responseCode = "200", description = "Liste der aktuellen Warnungen")
    @ApiErrorResponses
    public AlertList currentAlerts(

            @Parameter(description = """
                    Nur Warnungen dieser Stufe. Weggelassen bedeutet: alle \
                    Stufen ausser NORMAL.""",
                       example = "CRITICAL")
            @RequestParam(required = false) WarningLevel warningLevel,

            @Parameter(description = "Nur Stationen an diesem Fluss. Schreibweise egal.",
                       example = "Donau")
            @RequestParam(required = false) String river) {

        return new AlertList(alerts.findAlerts(warningLevel, river));
    }
}
