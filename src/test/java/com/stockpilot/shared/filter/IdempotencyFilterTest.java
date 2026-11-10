package com.stockpilot.shared.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.shared.filter.domain.IdempotencyRecord;
import com.stockpilot.shared.filter.repository.IdempotencyRecordRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private ObjectMapper objectMapper;
    private IdempotencyFilter idempotencyFilter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        idempotencyFilter = new IdempotencyFilter(idempotencyRecordRepository, objectMapper);
    }

    @Test
    @DisplayName("Returns 400 Bad Request when Idempotency-Key header is missing on POST /api/v1/orders")
    void missingIdempotencyKey_Returns400() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (req, res) -> {};

        idempotencyFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("missing-idempotency-key");
    }

    @Test
    @DisplayName("Returns 400 Bad Request when Idempotency-Key is not a valid UUID")
    void invalidIdempotencyKey_Returns400() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Idempotency-Key", "not-a-valid-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (req, res) -> {};

        idempotencyFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("invalid-idempotency-key");
    }

    @Test
    @DisplayName("Replays cached response and sets X-Idempotent-Replay header on Cache HIT")
    void cacheHit_ReplaysCachedResponse() throws ServletException, IOException {
        UUID key = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Idempotency-Key", key.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        IdempotencyRecord cachedRecord = new IdempotencyRecord(
                key, "POST", "/api/v1/orders", 201, "{\"orderId\": 101, \"status\": \"DRAFT\"}",
                Instant.now().plus(24, ChronoUnit.HOURS)
        );

        when(idempotencyRecordRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(cachedRecord));

        FilterChain filterChain = (req, res) -> {
            throw new AssertionError("filterChain must not be called on cache HIT");
        };

        idempotencyFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader("X-Idempotent-Replay")).isEqualTo("true");
        assertThat(response.getContentAsString()).isEqualTo("{\"orderId\": 101, \"status\": \"DRAFT\"}");
    }

    @Test
    @DisplayName("Returns 409 Conflict when request with same Idempotency-Key is currently in-flight")
    void inFlightConflict_Returns409() throws ServletException, IOException {
        UUID key = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Idempotency-Key", key.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        IdempotencyRecord inFlight = new IdempotencyRecord(
                key, "POST", "/api/v1/orders", 0, "IN_PROGRESS",
                Instant.now().plus(24, ChronoUnit.HOURS)
        );

        when(idempotencyRecordRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(inFlight));

        FilterChain filterChain = (req, res) -> {
            throw new AssertionError("filterChain must not be called on in-flight conflict");
        };

        idempotencyFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("idempotency-in-flight");
    }

    @Test
    @DisplayName("Executes filter chain and persists response on Cache MISS")
    void cacheMiss_ExecutesAndPersists() throws ServletException, IOException {
        UUID key = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("Idempotency-Key", key.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(idempotencyRecordRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(i -> i.getArgument(0));

        FilterChain filterChain = (req, res) -> {
            res.getOutputStream().write("{\"orderId\": 200}".getBytes());
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
        };

        idempotencyFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(201);
        verify(idempotencyRecordRepository).save(any(IdempotencyRecord.class));
    }
}
