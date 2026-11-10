package com.stockpilot.shared.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.shared.filter.domain.IdempotencyRecord;
import com.stockpilot.shared.filter.repository.IdempotencyRecordRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String PROBLEM_BASE_URI = "https://api.stockpilot.local/problems/";

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyRecordRepository idempotencyRecordRepository, ObjectMapper objectMapper) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only filter mutative operations on /api/v1/orders
        if (!path.startsWith("/api/v1/orders")) {
            return true;
        }

        return !("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String headerValue = request.getHeader(IDEMPOTENCY_HEADER);

        if (headerValue == null || headerValue.isBlank()) {
            writeProblemResponse(response, HttpStatus.BAD_REQUEST, "missing-idempotency-key",
                    "Missing required 'Idempotency-Key' header for mutative order operation.");
            return;
        }

        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(headerValue.trim());
        } catch (IllegalArgumentException ex) {
            writeProblemResponse(response, HttpStatus.BAD_REQUEST, "invalid-idempotency-key",
                    "Invalid 'Idempotency-Key' format. Must be a valid UUID v4.");
            return;
        }

        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            if (record.getResponseStatus() > 0) {
                if (record.getExpiresAt().isAfter(Instant.now())) {
                    log.info("Idempotency HIT for key '{}'. Replaying cached status {}.", idempotencyKey, record.getResponseStatus());
                    response.setStatus(record.getResponseStatus());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setHeader("X-Idempotent-Replay", "true");
                    response.getWriter().write(record.getResponseBody());
                    response.getWriter().flush();
                    return;
                } else {
                    idempotencyRecordRepository.delete(record);
                }
            } else {
                // In-flight concurrent execution conflict
                log.warn("Idempotency IN-FLIGHT conflict for key '{}'. Another request is being processed.", idempotencyKey);
                writeProblemResponse(response, HttpStatus.CONFLICT, "idempotency-in-flight",
                        String.format("A request with Idempotency-Key '%s' is currently being processed. Please retry shortly.", idempotencyKey));
                return;
            }
        }

        // Register in-flight placeholder (status = 0, IN_PROGRESS)
        IdempotencyRecord inFlightRecord = new IdempotencyRecord(
                idempotencyKey,
                request.getMethod(),
                request.getRequestURI(),
                0,
                "IN_PROGRESS",
                Instant.now().plus(24, ChronoUnit.HOURS)
        );

        try {
            inFlightRecord = idempotencyRecordRepository.saveAndFlush(inFlightRecord);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race condition inserting the same UUID
            log.warn("Concurrent duplicate insert race condition detected for Idempotency-Key '{}'", idempotencyKey);
            writeProblemResponse(response, HttpStatus.CONFLICT, "idempotency-in-flight",
                    String.format("A request with Idempotency-Key '%s' was initiated concurrently. Please retry shortly.", idempotencyKey));
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrappedResponse);

            int finalStatus = wrappedResponse.getStatus();
            if (finalStatus < 500) {
                byte[] content = wrappedResponse.getContentAsByteArray();
                String responseBody = new String(content, StandardCharsets.UTF_8);

                inFlightRecord.setResponseStatus(finalStatus);
                inFlightRecord.setResponseBody(responseBody);
                idempotencyRecordRepository.save(inFlightRecord);
                log.info("Persisted idempotent response for key '{}' with HTTP {}", idempotencyKey, finalStatus);
            } else {
                // Do not cache server errors (500) so client can safely retry
                idempotencyRecordRepository.delete(inFlightRecord);
            }

            wrappedResponse.copyBodyToResponse();
        } catch (Throwable t) {
            idempotencyRecordRepository.delete(inFlightRecord);
            throw t;
        }
    }

    private void writeProblemResponse(
            HttpServletResponse response,
            HttpStatus status,
            String errorType,
            String detail
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE_URI + errorType));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("timestamp", Instant.now());

        objectMapper.writeValue(response.getWriter(), problem);
    }
}
