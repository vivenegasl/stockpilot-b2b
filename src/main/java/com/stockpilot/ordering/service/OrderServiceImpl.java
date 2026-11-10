package com.stockpilot.ordering.service;

import com.stockpilot.catalog.domain.Product;
import com.stockpilot.catalog.exception.ProductNotFoundException;
import com.stockpilot.catalog.repository.ProductRepository;
import com.stockpilot.inventory.domain.Warehouse;
import com.stockpilot.inventory.dto.ReservationItem;
import com.stockpilot.inventory.exception.WarehouseNotFoundException;
import com.stockpilot.inventory.repository.WarehouseRepository;
import com.stockpilot.inventory.service.ReservationService;
import com.stockpilot.ordering.domain.OrderStatus;
import com.stockpilot.ordering.domain.PurchaseOrder;
import com.stockpilot.ordering.dto.CreateOrderRequest;
import com.stockpilot.ordering.dto.OrderItemRequest;
import com.stockpilot.ordering.dto.OrderLineResponse;
import com.stockpilot.ordering.dto.OrderResponse;
import com.stockpilot.ordering.exception.OrderNotFoundException;
import com.stockpilot.ordering.repository.PurchaseOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReservationService reservationService;

    public OrderServiceImpl(
            PurchaseOrderRepository purchaseOrderRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            ReservationService reservationService
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.reservationService = reservationService;
    }

    @Override
    public OrderResponse createDraftOrder(CreateOrderRequest request, UUID idempotencyKey) {
        Objects.requireNonNull(request, "CreateOrderRequest must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new WarehouseNotFoundException(request.warehouseId()));

        // Validate uniqueness of products within lines
        Set<Long> seenProductIds = new HashSet<>();
        for (OrderItemRequest item : request.items()) {
            if (!seenProductIds.add(item.productId())) {
                throw new IllegalArgumentException(
                        "Duplicate product ID '" + item.productId() + "' in order request lines.");
            }
        }

        String orderNumber = generateOrderNumber();
        PurchaseOrder order = new PurchaseOrder(orderNumber, idempotencyKey, warehouse, request.notes());

        for (OrderItemRequest item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ProductNotFoundException(item.productId()));

            if (!product.isActive()) {
                throw new IllegalStateException("Cannot create order with inactive SKU: " + product.getSku());
            }

            order.addLine(product, item.quantity(), product.getUnitPrice());
        }

        PurchaseOrder savedOrder = purchaseOrderRepository.save(order);
        log.info("Created DRAFT Order '{}' (ID: {}) with {} lines, total={}",
                savedOrder.getOrderNumber(), savedOrder.getId(), savedOrder.getLines().size(), savedOrder.getTotalNetAmount());

        return mapToResponse(savedOrder);
    }

    @Override
    public OrderResponse confirmOrder(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        log.info("Initiating atomic confirmation for Order '{}' (status: {})", order.getOrderNumber(), order.getStatus());

        // Validate and transition state to CONFIRMED
        order.confirm();

        // Prepare reservation items
        List<ReservationItem> reservationItems = order.getLines().stream()
                .map(line -> new ReservationItem(line.getProduct().getId(), line.getQuantity()))
                .toList();

        // Delegate atomic reservation with deterministic lock order
        reservationService.reserveStock(
                order.getId(),
                order.getOrderNumber(),
                order.getWarehouse().getId(),
                reservationItems
        );

        PurchaseOrder confirmedOrder = purchaseOrderRepository.save(order);
        log.info("Order '{}' successfully CONFIRMED and stock reserved.", confirmedOrder.getOrderNumber());

        return mapToResponse(confirmedOrder);
    }

    @Override
    public OrderResponse cancelOrder(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        log.info("Initiating cancellation for Order '{}' (current status: {})", order.getOrderNumber(), order.getStatus());

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            reservationService.releaseStock(order.getId(), order.getOrderNumber());
        }

        order.cancel();
        PurchaseOrder cancelledOrder = purchaseOrderRepository.save(order);
        log.info("Order '{}' successfully CANCELLED and any active reservations released.", cancelledOrder.getOrderNumber());

        return mapToResponse(cancelledOrder);
    }

    @Override
    public OrderResponse fulfillOrder(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        log.info("Initiating fulfillment for Order '{}' (status: {})", order.getOrderNumber(), order.getStatus());

        reservationService.fulfillStock(order.getId(), order.getOrderNumber());

        order.fulfill();
        PurchaseOrder fulfilledOrder = purchaseOrderRepository.save(order);
        log.info("Order '{}' successfully FULFILLED and physical stock deducted.", fulfilledOrder.getOrderNumber());

        return mapToResponse(fulfilledOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return mapToResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        PurchaseOrder order = purchaseOrderRepository.findByOrderNumberWithDetails(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException(orderNumber));
        return mapToResponse(order);
    }

    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return String.format("ORD-%s-%s", datePart, randomSuffix);
    }

    private OrderResponse mapToResponse(PurchaseOrder order) {
        List<OrderLineResponse> lineResponses = order.getLines().stream()
                .map(line -> new OrderLineResponse(
                        line.getId(),
                        line.getProduct().getId(),
                        line.getProduct().getSku(),
                        line.getProduct().getName(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getLineTotal()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getIdempotencyKey(),
                order.getStatus(),
                order.getWarehouse().getId(),
                order.getWarehouse().getCode(),
                order.getWarehouse().getName(),
                order.getTotalNetAmount(),
                order.getNotes(),
                order.getVersion(),
                lineResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
