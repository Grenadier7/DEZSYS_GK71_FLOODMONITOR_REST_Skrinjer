package at.tgm.dezsys.floodmonitor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Kopfdaten der OpenAPI-Dokumentation (Angabe Punkt 10).
 * <p>
 * springdoc liest die Controller beim Start per Reflection aus und baut daraus
 * das Dokument unter {@code /v3/api-docs}. Pfade, Parameter, Typen und Schemas
 * entstehen dadurch von selbst - was es <em>nicht</em> aus dem Code ablesen
 * kann, sind Titel, Beschreibung, Version und die Angabe zur
 * Authentifizierung. Genau das setzt diese Bean.
 * <p>
 * Alternative waere die Annotation {@code @OpenAPIDefinition} an der
 * Application-Klasse. Eine Bean ist hier vorgezogen, weil sich Texte in
 * normalem Java leichter formatieren lassen als in Annotationswerten.
 * <p>
 * Aufrufbar nach dem Start:
 * <ul>
 *   <li>{@code http://localhost:8080/swagger-ui.html} - interaktive Oberflaeche</li>
 *   <li>{@code http://localhost:8080/v3/api-docs} - das Dokument als JSON</li>
 *   <li>{@code http://localhost:8080/v3/api-docs.yaml} - dasselbe als YAML</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI floodmonitorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hochwasser-Fruehwarnsystem - REST API")
                        .version("1.0.0")
                        .description("""
                                REST-Schnittstelle eines Hochwasser-Fruehwarnsystems. \
                                Mehrere Messstationen entlang von Fluessen erfassen \
                                Wasserstand, Durchflussmenge, Niederschlag und weitere \
                                Umgebungsdaten.

                                **Datenformate.** Alle Endpunkte liefern JSON und XML. \
                                Das Format waehlt der Client ueber den `Accept`-Header \
                                (`application/json` oder `application/xml`). Ohne Header \
                                wird JSON geliefert, bei einem anderen Format antwortet \
                                die API mit `406 Not Acceptable`. JSON und XML enthalten \
                                stets dieselben fachlichen Informationen.

                                **Warnstufen.** Die Warnstufe wird ausschliesslich \
                                serverseitig berechnet und kann vom Client nicht \
                                vorgegeben werden:
                                * `NORMAL` - Wasserstand unter dem Warnwert der Station
                                * `WARNING` - Wasserstand ab dem Warnwert
                                * `CRITICAL` - Wasserstand ab dem kritischen Wert
                                * `UNKNOWN` - keine aktuelle oder gueltige Messung \
                                (Station OFFLINE, noch nie gemeldet, oder letzte Messung \
                                aelter als `floodmonitor.measurement-max-age`)

                                **Fehler.** Jeder Fehler wird als `ApiError` mit \
                                `timestamp`, `status`, `error`, `message` und `path` \
                                zurueckgegeben, ebenfalls in JSON oder XML. \
                                Stacktraces werden nie ausgeliefert.

                                **Authentifizierung.** Diese API ist bewusst offen: es \
                                gibt keine Authentifizierung und keine Autorisierung. \
                                Alle Endpunkte sind lesend (`GET`) und liefern keine \
                                personenbezogenen Daten. Die Statuscodes `401` und `403` \
                                treten daher nicht auf. Ebenso entfallen `201`, `204` und \
                                `409`, weil es keine schreibenden Endpunkte gibt.
                                """)
                        .contact(new Contact().name("Schulprojekt DEZSYS GK71"))
                        .license(new License().name("Nur zu Ausbildungszwecken")))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Lokale Entwicklungsinstanz")));
    }
}
