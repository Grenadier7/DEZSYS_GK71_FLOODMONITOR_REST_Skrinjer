package at.tgm.dezsys.floodmonitor.model;

import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;

/**
 * Eine aktuelle Warnung fuer /api/v1/alerts.
 * <p>
 * Eine Warnung ist bewusst kein {@link Measurement}: der Client, der die
 * Warnliste anzeigt, braucht Stationsname und Fluss (sonst muesste er fuer
 * jede Warnung eine zweite Anfrage an /stations/{id} stellen), dafuer aber
 * keine Temperatur und keinen Akkustand. Ein eigenes Read-Model haelt die
 * Antwort klein und macht die Bedeutung der Felder eindeutig.
 * <p>
 * Die Schwellwerte der Station kommen mit, damit der Client den Pegel
 * einordnen kann ("318 von 310 cm") ohne die Station nachzuladen.
 *
 * @param waterLevel   Pegel der letzten Messung in cm
 * @param timestamp    Zeitpunkt der letzten Messung - bei einer UNKNOWN-Warnung
 *                     also der Zeitpunkt, zu dem die Station zuletzt gemeldet hat
 */
@JacksonXmlRootElement(localName = "alert")
public record Alert(
        String stationId,
        String stationName,
        String river,
        WarningLevel warningLevel,
        double waterLevel,
        double warningWaterLevel,
        double criticalWaterLevel,
        Instant timestamp
) {
}
