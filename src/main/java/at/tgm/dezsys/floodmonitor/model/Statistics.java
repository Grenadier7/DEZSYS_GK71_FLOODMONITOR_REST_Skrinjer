package at.tgm.dezsys.floodmonitor.model;

import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Statistische Auswertung ueber eine Menge von Messwerten.
 * <p>
 * Alle Felder sind primitiv (kein {@code Double}), damit JSON und XML garantiert
 * dieselben Felder enthalten: ein {@code null} wuerde in JSON als
 * {@code "minWaterLevel": null} erscheinen, in XML dagegen ganz fehlen - und die
 * Angabe verlangt fachlich identische Antworten in beiden Formaten.
 * <p>
 * Bei {@code measurementCount == 0} sind alle Kennzahlen 0. Das ist der
 * Preis fuer die Primitiven: {@code measurementCount} ist das Feld, an dem der
 * Client erkennt, ob die anderen Werte eine Aussage haben.
 *
 * @param avgWaterLevel  arithmetisches Mittel des Pegels in cm
 * @param totalRainfall  Summe der Niederschlagswerte in mm/h (keine echte
 *                       Regenmenge, sondern die Summe der Stundenwerte)
 * @param warningCount   Messwerte mit Warnstufe WARNING oder CRITICAL
 * @param criticalCount  Messwerte mit Warnstufe CRITICAL
 */
@JacksonXmlRootElement(localName = "statistics")
public record Statistics(
        String stationId,
        int measurementCount,
        double minWaterLevel,
        double maxWaterLevel,
        double avgWaterLevel,
        double avgFlowRate,
        double totalRainfall,
        int warningCount,
        int criticalCount
) {
}
