package com.stockpilot.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("InsufficientStockException produces RFC 7807 ProblemDetail with 409 Conflict")
    void handleInsufficientStockException_returnsConflictProblemDetail() {
        InsufficientStockException ex = new InsufficientStockException("PLC-SIEM-S71500", "WH-SCL-CENTRAL", 5, 2);

        ProblemDetail problem = handler.handleBusinessException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Conflict");
        assertThat(problem.getDetail()).contains("PLC-SIEM-S71500", "WH-SCL-CENTRAL", "requested 5", "available 2");
        assertThat(problem.getType()).isEqualTo(URI.create("https://api.stockpilot.local/problems/insufficient-stock"));
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("ResourceNotFoundException produces RFC 7807 ProblemDetail with 404 Not Found")
    void handleResourceNotFoundException_returnsNotFoundProblemDetail() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Product", "SKU-999");

        ProblemDetail problem = handler.handleBusinessException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getDetail()).contains("Product", "SKU-999");
        assertThat(problem.getType()).isEqualTo(URI.create("https://api.stockpilot.local/problems/resource-not-found"));
    }

    @Test
    @DisplayName("PessimisticLockingFailureException produces 503 Service Unavailable with retry advice")
    void handleLockTimeoutException_returnsServiceUnavailable() {
        PessimisticLockingFailureException ex = new PessimisticLockingFailureException("Lock acquisition timeout");

        ProblemDetail problem = handler.handleLockTimeoutException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getTitle()).isEqualTo("Lock Contention Timeout");
        assertThat(problem.getDetail()).contains("high contention");
        assertThat(problem.getType()).isEqualTo(URI.create("https://api.stockpilot.local/problems/lock-timeout"));
    }
}
