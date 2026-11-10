package com.stockpilot.ordering.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ordering.domain.OrderStatus;
import com.stockpilot.ordering.dto.CreateOrderRequest;
import com.stockpilot.ordering.dto.OrderItemRequest;
import com.stockpilot.ordering.dto.OrderLineResponse;
import com.stockpilot.ordering.dto.OrderResponse;
import com.stockpilot.ordering.exception.InvalidOrderStateException;
import com.stockpilot.ordering.exception.OrderNotFoundException;
import com.stockpilot.ordering.service.OrderService;
import com.stockpilot.shared.exception.GlobalExceptionHandler;
import com.stockpilot.shared.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("POST /api/v1/orders returns 201 Created and Location header on valid request")
    void createOrder_Returns201() throws Exception {
        UUID key = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                List.of(new OrderItemRequest(10L, 2)),
                "Nota de pedido"
        );

        OrderResponse response = new OrderResponse(
                101L,
                "ORD-20261023-0001",
                key,
                OrderStatus.DRAFT,
                1L,
                "WH-SCL-CENTRAL",
                "Centro Pudahuel",
                new BigDecimal("2500000.00"),
                "Nota de pedido",
                0,
                List.of(new OrderLineResponse(1L, 10L, "PLC-SIEM-S71500", "Siemens S7-1500", 2, new BigDecimal("1250000.00"), new BigDecimal("2500000.00"))),
                Instant.now(),
                Instant.now()
        );

        when(orderService.createDraftOrder(any(CreateOrderRequest.class), eq(key))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/orders/101")))
                .andExpect(jsonPath("$.id", is(101)))
                .andExpect(jsonPath("$.orderNumber", is("ORD-20261023-0001")))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.totalNetAmount", is(2500000.00)));
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{id}/confirm returns 200 OK when stock reservation succeeds")
    void confirmOrder_Returns200() throws Exception {
        UUID key = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                101L,
                "ORD-20261023-0001",
                key,
                OrderStatus.CONFIRMED,
                1L,
                "WH-SCL-CENTRAL",
                "Centro Pudahuel",
                new BigDecimal("2500000.00"),
                "Nota",
                0,
                List.of(),
                Instant.now(),
                Instant.now()
        );

        when(orderService.confirmOrder(101L)).thenReturn(response);

        mockMvc.perform(put("/api/v1/orders/101/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(101)))
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{id}/confirm returns 409 Conflict ProblemDetail on InsufficientStockException")
    void confirmOrder_InsufficientStock_Returns409() throws Exception {
        when(orderService.confirmOrder(101L))
                .thenThrow(new InsufficientStockException("PLC-SIEM-S71500", "WH-SCL-CENTRAL", 5, 2));

        mockMvc.perform(put("/api/v1/orders/101/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("https://api.stockpilot.local/problems/insufficient-stock")))
                .andExpect(jsonPath("$.detail", containsString("requested 5, available 2")));
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{id}/confirm returns 409 Conflict ProblemDetail on InvalidOrderStateException")
    void confirmOrder_InvalidState_Returns409() throws Exception {
        when(orderService.confirmOrder(101L))
                .thenThrow(new InvalidOrderStateException("ORD-001", "CANCELLED", "confirm"));

        mockMvc.perform(put("/api/v1/orders/101/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("https://api.stockpilot.local/problems/invalid-order-state")))
                .andExpect(jsonPath("$.detail", containsString("current state is 'CANCELLED'")));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} returns 404 Not Found ProblemDetail on OrderNotFoundException")
    void getOrder_NotFound_Returns404() throws Exception {
        when(orderService.getOrderById(999L)).thenThrow(new OrderNotFoundException(999L));

        mockMvc.perform(get("/api/v1/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", is("https://api.stockpilot.local/problems/resource-not-found")));
    }
}
