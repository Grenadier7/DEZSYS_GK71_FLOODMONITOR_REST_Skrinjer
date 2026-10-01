package at.tgm.dezsys.floodmonitor.exceptions;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;

/**
 * Einheitlicher Fehler-Body fuer alle Fehlerantworten (Angabe Punkt 6).
 * <p>
 * Wichtig: Das ist ein ganz normales Datenobjekt, KEIN Exception-Typ. Es laeuft
 * durch dieselben HttpMessageConverter wie Station und Measurement und kommt
 * deshalb je nach Accept-Header als JSON oder als XML heraus.
 * <p>
 * Ausnahme: Der 406-Handler erzwingt bewusst JSON. Der Client hat dort ein
 * Format verlangt, das die API nicht erzeugen kann - wuerde sie versuchen, die
 * Fehlermeldung in genau diesem Format zu liefern, faende sie wieder keinen
 * Converter und der Handler liefe ins Leere.
 *
 * @param timestamp Zeitpunkt des Fehlers
 * @param status    HTTP-Statuscode, z. B. 404
 * @param error     Standardtext zum Status, z. B. "Not Found"
 * @param message   fachliche Meldung fuer den Client
 * @param path      der angefragte Pfad
 */
@JacksonXmlRootElement(localName = "error")
@Schema(name = "ApiError",
        description = "Einheitlicher Fehler-Body. Wird bei jedem Fehlerstatus "
                    + "geliefert, in JSON oder XML je nach Accept-Header. "
                    + "Enthaelt nie einen Stacktrace.")
public record ApiError(

        @Schema(description = "Zeitpunkt des Fehlers, ISO-8601 in UTC",
                example = "2026-05-01T10:20:00Z")
        Instant timestamp,

        @Schema(description = "HTTP-Statuscode", example = "404")
        int status,

        @Schema(description = "Standardtext zum Statuscode", example = "Not Found")
        String error,

        @Schema(description = "Fachliche Meldung fuer den Client",
                example = "The station with id ST-999 does not exist")
        String message,

        @Schema(description = "Der angefragte Pfad", example = "/api/v1/stations/ST-999")
        String path
) {
}
