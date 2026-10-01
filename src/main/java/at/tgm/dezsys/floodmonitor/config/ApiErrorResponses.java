package at.tgm.dezsys.floodmonitor.config;

import at.tgm.dezsys.floodmonitor.exceptions.ApiError;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Die Fehlerantworten, die an jedem Endpunkt dieser API auftreten koennen.
 * <p>
 * Eine <em>zusammengesetzte Annotation</em> (composed annotation): sie traegt
 * selbst die {@code @ApiResponse}-Annotationen und gibt sie an jede Methode
 * weiter, die mit ihr annotiert ist. Ohne diesen Umweg stuenden an jeder der
 * sechs Controller-Methoden dieselben drei Bloecke - zwanzig Zeilen
 * Dokumentation pro Methode, die bei einer Aenderung an sechs Stellen
 * nachgezogen werden muessten.
 * <p>
 * Dasselbe Muster benutzt Spring selbst: {@code @RestController} ist nichts
 * anderes als {@code @Controller} plus {@code @ResponseBody}.
 * <p>
 * {@code @Retention(RUNTIME)} ist Pflicht - springdoc liest die Annotationen
 * beim Start per Reflection. Mit dem Default {@code CLASS} waeren sie zur
 * Laufzeit nicht mehr sichtbar und die Dokumentation bliebe leer.
 * <p>
 * Status 404 steht hier <b>nicht</b> drin: den gibt es nur bei Endpunkten mit
 * {@code {stationId}} im Pfad. Er wird dort einzeln annotiert.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiResponse(
        responseCode = "400",
        description = """
                Ungueltige Eingabe. Tritt auf bei: from liegt nach to, \
                limit ausserhalb 1 bis 1000, limit keine Zahl, \
                ungueltiges Datumsformat, unbekannte Warnstufe.""",
        content = {
                @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ApiError.class)),
                @Content(mediaType = "application/xml",
                        schema = @Schema(implementation = ApiError.class))
        })
@ApiResponse(
        responseCode = "406",
        description = """
                Das im Accept-Header verlangte Format wird nicht unterstuetzt. \
                Moeglich sind nur application/json und application/xml.""",
        content = {
                @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ApiError.class))
        })
@ApiResponse(
        responseCode = "500",
        description = """
                Interner Serverfehler. Die Antwort enthaelt nie einen \
                Stacktrace - der steht ausschliesslich im Server-Log.""",
        content = {
                @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ApiError.class)),
                @Content(mediaType = "application/xml",
                        schema = @Schema(implementation = ApiError.class))
        })
public @interface ApiErrorResponses {
}
