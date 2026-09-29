package at.tgm.dezsys.floodmonitor.model;

import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;

/**
 * Eine einzelne Messung einer Station.
 * <p>
 * {@link Instant} ist hier der richtige Typ aus {@code java.time}: die Angabe
 * verlangt Zeitstempel in UTC ({@code 2026-05-01T10:15:00Z}). {@code LocalDateTime}
 * haette keine Zeitzone und wuerde das {@code Z} beim Serialisieren verlieren.
 *
 * @param waterLevel   Wasserstand in cm
 * @param flowRate     Durchflussmenge in m3/s
 * @param rainfall     Niederschlag der letzten Stunde in mm/h
 * @param temperature  Lufttemperatur in Grad Celsius
 * @param batteryLevel Akkustand der Station in Prozent (0-100)
 * @param warningLevel serverseitig berechnet, nie vom Client vorgegeben
 */
@JacksonXmlRootElement(localName = "measurement")
public record Measurement(
        String stationId,
        Instant timestamp,
        double waterLevel,
        double flowRate,
        double rainfall,
        double temperature,
        int batteryLevel,
        StationStatus status,
        WarningLevel warningLevel
) {
}
