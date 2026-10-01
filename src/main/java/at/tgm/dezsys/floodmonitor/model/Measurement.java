package at.tgm.dezsys.floodmonitor.model;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;

/**
 * Eine einzelne Messung einer Station.
 * <p>
 * {@link Instant} ist hier der richtige Typ aus {@code java.time}: die Angabe
 * verlangt Zeitstempel in UTC ({@code 2026-05-01T10:15:00Z}). {@code LocalDateTime}
 * haette keine Zeitzone und wuerde das {@code Z} beim Serialisieren verlieren.
 * <p>
 * Die {@code @Schema}-Annotationen dienen nur der OpenAPI-Dokumentation. Sie
 * tragen die Einheiten, die man am nackten {@code double} nicht sehen kann -
 * ohne sie wuesste ein fremder Client nicht, ob {@code waterLevel} in cm oder
 * in m gemeint ist.
 *
 * @param waterLevel   Wasserstand in cm
 * @param flowRate     Durchflussmenge in m3/s
 * @param rainfall     Niederschlag der letzten Stunde in mm/h
 * @param temperature  Lufttemperatur in Grad Celsius
 * @param batteryLevel Akkustand der Station in Prozent (0-100)
 * @param warningLevel serverseitig berechnet, nie vom Client vorgegeben
 */
@JacksonXmlRootElement(localName = "measurement")
@Schema(description = "Eine einzelne Messung einer Station")
public record Measurement(

        @Schema(description = "Technische ID der Station", example = "ST-001")
        String stationId,

        @Schema(description = "Zeitpunkt der Messung, ISO-8601 in UTC",
                example = "2026-05-01T10:15:00Z")
        Instant timestamp,

        @Schema(description = "Aktueller Wasserstand in cm. Nie negativ.",
                example = "318.4")
        double waterLevel,

        @Schema(description = "Durchflussmenge in m3/s. Waechst ueberproportional "
                            + "mit dem Wasserstand.",
                example = "742.6")
        double flowRate,

        @Schema(description = "Niederschlag der letzten Stunde in mm/h", example = "12.7")
        double rainfall,

        @Schema(description = "Lufttemperatur in Grad Celsius", example = "14.3")
        double temperature,

        @Schema(description = "Akkustand der Messstation in Prozent",
                minimum = "0", maximum = "100", example = "87")
        int batteryLevel,

        @Schema(description = "Technischer Zustand der Station", example = "ONLINE")
        StationStatus status,

        @Schema(description = "Serverseitig berechnete Hochwasserwarnstufe. "
                            + "Kann vom Client nicht vorgegeben werden.",
                example = "WARNING")
        WarningLevel warningLevel
) {
}
