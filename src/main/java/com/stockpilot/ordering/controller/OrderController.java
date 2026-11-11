package com.stockpilot.ordering.controller;

import com.stockpilot.ordering.domain.OrderStatus;
import com.stockpilot.ordering.dto.CreateOrderRequest;
import com.stockpilot.ordering.dto.OrderResponse;
import com.stockpilot.ordering.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Endpoints for B2B Purchase Order lifecycle, atomic reservations and fulfillment")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Create a new Purchase Order in DRAFT state", description = "Initializes an order without committing stock. Requires an Idempotency-Key header.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully in DRAFT status"),
            @ApiResponse(responseCode = "400", description = "Validation error or missing/invalid Idempotency-Key"),
            @ApiResponse(responseCode = "404", description = "Warehouse or Product SKU not found")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "UUID v4 idempotency token", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKeyHeader,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        UUID idempotencyKey = UUID.fromString(idempotencyKeyHeader);
        OrderResponse response = orderService.createDraftOrder(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id())).body(response);
    }

    @PutMapping("/{id}/confirm")
    @Operation(summary = "Confirm order with atomic pessimistic stock reservation", description = "Transitions order from DRAFT to CONFIRMED. Acquires SELECT ... FOR UPDATE locks ordered by productId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order confirmed and stock reserved"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Insufficient stock or invalid order state")
    })
    public ResponseEntity<OrderResponse> confirmOrder(@PathVariable Long id) {
        OrderResponse response = orderService.confirmOrder(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order and deterministically release active reservations", description = "Transitions order to CANCELLED. If previously CONFIRMED, active reservations are returned to stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Invalid order state transition")
    })
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        OrderResponse response = orderService.cancelOrder(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/fulfill")
    @Operation(summary = "Fulfill an order with physical warehouse stock reduction", description = "Transitions order from CONFIRMED to FULFILLED, permanently deducting total_qty and closing reservations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order fulfilled successfully"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Invalid order state transition (must be CONFIRMED)")
    })
    public ResponseEntity<OrderResponse> fulfillOrder(@PathVariable Long id) {
        OrderResponse response = orderService.fulfillOrder(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details by ID", description = "Returns full order information including lines and status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order details retrieved"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List purchase orders with pagination and status filter", description = "Returns a paged list of purchase orders, optionally filtered by status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders page retrieved successfully")
    })
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @Parameter(description = "Optional status filter (DRAFT, CONFIRMED, CANCELLED, FULFILLED)")
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(orderService.getOrders(status, pageable));
    }
}
