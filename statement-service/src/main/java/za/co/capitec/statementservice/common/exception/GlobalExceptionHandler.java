package za.co.capitec.statementservice.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StatementNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(StatementNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedStatementException.class)
    ResponseEntity<Map<String, Object>> denied(AccessDeniedStatementException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(InvalidStatementRequestException.class)
    ResponseEntity<Map<String, Object>> invalid(InvalidStatementRequestException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(za.co.capitec.statementservice.statement.service.InsufficientEligibleTransactionsException.class)
    ResponseEntity<Map<String, Object>> insufficient(
            za.co.capitec.statementservice.statement.service.InsufficientEligibleTransactionsException e) {
        Map<String, Object> body = base(HttpStatus.CONFLICT, e.getMessage());
        body.put("eligibleCount", e.getEligibleCount());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DownloadLinkException.class)
    ResponseEntity<Map<String, Object>> download(DownloadLinkException e) {
        HttpStatus status = switch (e.getReason()) {
            case INVALID -> HttpStatus.UNAUTHORIZED;
            case EXPIRED -> HttpStatus.GONE;
            case REVOKED -> HttpStatus.FORBIDDEN;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        return build(status, e.getMessage());
    }

    @ExceptionHandler(UpstreamServiceException.class)
    ResponseEntity<Map<String, Object>> upstream(UpstreamServiceException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(StatementGenerationException.class)
    ResponseEntity<Map<String, Object>> generation(StatementGenerationException e) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Statement generation failed: " + e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, "Request validation failed");
        body.put("fieldErrors", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> malformed(Exception e) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request data or parameter");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException e) {
        return build(HttpStatus.CONFLICT, "The request conflicts with an existing record");
    }

    private static ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(base(status, message));
    }

    private static Map<String, Object> base(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
