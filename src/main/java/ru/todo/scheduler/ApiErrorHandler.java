package ru.todo.scheduler;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.UncategorizedSQLException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> apiError(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
    }
    private final Counter lockConflicts;

    public ApiErrorHandler(MeterRegistry registry) {
        this.lockConflicts = registry.counter("todo.tasks.lock.conflicts");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> lockingFailure(
            PessimisticLockingFailureException exception) {
        return lockingConflict();
    }

private ResponseEntity<Map<String, String>> lockingConflict() {
    lockConflicts.increment();
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "задача временно занята, повторите запрос"));
}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid request body"));
    }

    @ExceptionHandler(UncategorizedSQLException.class)
public ResponseEntity<Map<String, String>> sqlFailure(
        UncategorizedSQLException exception) {
    String sqlState = exception.getSQLException().getSQLState();

    if ("55P03".equals(sqlState) || "40P01".equals(sqlState)) {
        return lockingConflict();
    }

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "database request failed"));
}
}


