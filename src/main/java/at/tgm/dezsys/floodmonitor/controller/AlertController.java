package at.tgm.dezsys.floodmonitor.controller;

import at.tgm.dezsys.floodmonitor.model.AlertList;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import at.tgm.dezsys.floodmonitor.service.AlertService;
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
    @GetMapping("/alerts")
    public AlertList currentAlerts(
            @RequestParam(required = false) WarningLevel warningLevel,
            @RequestParam(required = false) String river) {
        return new AlertList(alerts.findAlerts(warningLevel, river));
    }
}
