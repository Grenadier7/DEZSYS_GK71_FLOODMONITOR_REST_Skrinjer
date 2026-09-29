package at.tgm.dezsys.floodmonitor.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
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
