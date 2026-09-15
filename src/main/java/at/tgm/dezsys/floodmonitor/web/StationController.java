package at.tgm.dezsys.floodmonitor.web;

import at.tgm.dezsys.floodmonitor.model.Station;
import at.tgm.dezsys.floodmonitor.service.StationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP-Schnittstelle fuer Messstationen.
 * <p>
 * {@code @RestController} = {@code @Controller} + {@code @ResponseBody}: der
 * Rueckgabewert wird nicht als View-Name interpretiert, sondern vom passenden
 * HttpMessageConverter serialisiert. Welcher Converter das ist, entscheidet der
 * {@code Accept}-Header des Requests - das ist die Content Negotiation.
 * <p>
 * {@code @RequestMapping("/api/v1")} setzt den gemeinsamen Praefix, damit die
 * Basis-URL aus der Angabe nicht in jeder Methode wiederholt werden muss.
 */
@RestController
@RequestMapping("/api/v1")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    /**
     * GET /api/v1/stations
     * <p>
     * TODO Schritt 16: Rueckgabetyp auf einen Wrapper-Record aendern, sonst
     * erzeugt Jackson bei {@code Accept: application/xml} ein
     * {@code <ArrayList><item>...} statt {@code <stations><station>...}.
     * <p>
     * TODO Schritt 21: Query-Parameter river, isActive und warningLevel ergaenzen.
     */
    @GetMapping("/stations")
    public List<Station> allStations() {
        return stationService.findAll();
    }
}
