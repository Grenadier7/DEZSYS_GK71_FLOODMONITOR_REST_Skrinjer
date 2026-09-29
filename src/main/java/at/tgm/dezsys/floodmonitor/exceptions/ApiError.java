package at.tgm.dezsys.floodmonitor.exceptions;

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
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
