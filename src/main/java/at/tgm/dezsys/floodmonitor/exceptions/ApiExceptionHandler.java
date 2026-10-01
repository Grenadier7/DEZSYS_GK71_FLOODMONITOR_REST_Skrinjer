package at.tgm.dezsys.floodmonitor.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Zentrale Fehlerbehandlung (Angabe Punkt 6).
 * <p>
 * {@code @RestControllerAdvice} = {@code @ControllerAdvice} + {@code @ResponseBody}:
 * die Handler gelten fuer alle Controller, und ihr Rueckgabewert geht durch
 * dieselbe Content Negotiation wie eine normale Antwort. Ein Fehler kommt
 * deshalb automatisch als JSON <em>oder</em> als XML zurueck, je nach
 * {@code Accept}-Header - ohne dass hier etwas dafuer getan werden muesste.
 * <p>
 * <b>Reihenfolge ist wichtig.</b> Spring waehlt den Handler mit dem
 * spezifischsten passenden Exception-Typ, nicht den erstgenannten. Der
 * Catch-all fuer {@link Exception} greift also nur, wenn kein anderer passt.
 * Trotzdem ist er die gefaehrlichste Methode der Klasse: ohne die Handler fuer
 * Springs eigene Web-Exceptions wuerde er aus einem Tippfehler in der URL
 * (eigentlich 404) oder einem falschen HTTP-Verb (eigentlich 405) ein 500
 * machen - der Server behauptete dann, <em>er</em> habe einen Fehler, obwohl
 * der Client falsch gefragt hat.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // ---------- 404 Not Found ----------

    @ExceptionHandler(StationNotFoundException.class)
    public ResponseEntity<ApiError> handleStationNotFound(StationNotFoundException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(MeasurementNotFoundException.class)
    public ResponseEntity<ApiError> handleMeasurementNotFound(MeasurementNotFoundException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Pfad existiert gar nicht, z. B. {@code /api/v1/statonen} oder
     * {@code /api/v99/stations}.
     * <p>
     * Ohne diesen Handler landet der Fall beim Catch-all und wird zu einem 500.
     * Ein Tippfehler des Clients ist aber kein Serverfehler.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND,
                "Der Pfad '%s' existiert nicht".formatted(request.getRequestURI()), request);
    }

    // ---------- 405 Method Not Allowed ----------

    /**
     * Richtiger Pfad, falsches Verb - z. B. {@code POST /api/v1/stations}, wo es
     * nur ein {@code GET} gibt. Die Angabe listet 405 nicht auf, aber 500 waere
     * hier klar falsch, und der {@code Allow}-Header gehoert laut HTTP-Standard
     * zwingend zu dieser Antwort.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        String allowed = ex.getSupportedHttpMethods() == null ? "GET"
                : ex.getSupportedHttpMethods().stream()
                        .map(Object::toString).collect(Collectors.joining(", "));

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                "Methode %s ist fuer diesen Pfad nicht erlaubt. Erlaubt: %s"
                        .formatted(ex.getMethod(), allowed),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header("Allow", allowed)
                .body(body);
    }

    // ---------- 400 Bad Request ----------

    /** from liegt nach to - selbst geprüft im Service. */
    @ExceptionHandler(InvalidRangeException.class)
    public ResponseEntity<ApiError> handleInvalidRange(InvalidRangeException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** ?warningLevel=HOCH, ?limit=abc, ?from=kein-datum */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String message = "Ungueltiger Wert '%s' fuer Parameter '%s'"
                .formatted(ex.getValue(), ex.getName());
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /** @Min/@Max an einem @RequestParam verletzt, z. B. limit=99999 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleParameterValidation(HandlerMethodValidationException ex,
                                                              HttpServletRequest request) {
        String message = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /** Ein als {@code required} deklarierter Query-Parameter fehlt. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Pflichtparameter '%s' fehlt".formatted(ex.getParameterName()), request);
    }

    /** @Valid @RequestBody verletzt - erst ab POST/PATCH relevant. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // ---------- 406 Not Acceptable ----------

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleNotAcceptable(HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.NOT_ACCEPTABLE.value(),
                HttpStatus.NOT_ACCEPTABLE.getReasonPhrase(),
                "Nur application/json und application/xml werden unterstuetzt",
                request.getRequestURI());

        // Content-Type erzwingen: der Client hat ein Format verlangt, das wir
        // nicht koennen - ohne diese Zeile faende Spring auch fuer diese
        // Fehlerantwort keinen Converter und der Handler liefe ins Leere.
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    // ---------- 415 Unsupported Media Type ----------

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                               HttpServletRequest request) {
        String message = "Content-Type '%s' wird nicht unterstuetzt".formatted(ex.getContentType());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message, request);
    }

    // ---------- 500 Internal Server Error ----------

    /**
     * Letzte Instanz fuer alles, was keinen eigenen Handler hat.
     * <p>
     * Zwei Dinge passieren hier bewusst:
     * <ol>
     *   <li>Der Stacktrace geht ins Log, aber nicht an den Client - die Angabe
     *       verlangt das ausdruecklich. Ein Stacktrace verraet Paketnamen,
     *       Framework-Versionen und Zeilennummern.</li>
     *   <li>Bringt die Exception selbst einen HTTP-Status mit (alle Web-Exceptions
     *       von Spring implementieren {@link ErrorResponse}), wird dieser benutzt
     *       statt 500. Das ist das Sicherheitsnetz dafuer, dass kuenftige
     *       Framework-Exceptions nicht stillschweigend als Serverfehler
     *       erscheinen, nur weil hier ein Handler fehlt.</li>
     * </ol>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            log.warn("Request-Fehler {} bei {}: {}",
                    status.value(), request.getRequestURI(), ex.getMessage());
            return build(status, status.getReasonPhrase(), request);
        }

        // Ins Log mit vollem Stacktrace - aber NICHT an den Client.
        log.error("Unerwarteter Fehler bei {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Interner Serverfehler", request);
    }

    // ---------- gemeinsamer Bauplan ----------

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
