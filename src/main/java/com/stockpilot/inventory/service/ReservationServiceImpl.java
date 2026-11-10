package com.stockpilot.inventory.service;

import com.stockpilot.audit.domain.MovementType;
import com.stockpilot.audit.service.LedgerService;
import com.stockpilot.catalog.domain.Product;
import com.stockpilot.catalog.exception.ProductNotFoundException;
import com.stockpilot.catalog.repository.ProductRepository;
import com.stockpilot.inventory.domain.ReservationStatus;
import com.stockpilot.inventory.domain.StockLevel;
import com.stockpilot.inventory.domain.StockReservation;
import com.stockpilot.inventory.domain.Warehouse;
import com.stockpilot.inventory.dto.ReservationItem;
import com.stockpilot.inventory.exception.WarehouseNotFoundException;
import com.stockpilot.inventory.repository.StockLevelRepository;
import com.stockpilot.inventory.repository.StockReservationRepository;
import com.stockpilot.inventory.repository.WarehouseRepository;
import com.stockpilot.shared.exception.InsufficientStockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class ReservationServiceImpl implements ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);

    private final StockLevelRepository stockLevelRepository;
    private final StockReservationRepository stockReservationRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final LedgerService ledgerService;

    public ReservationServiceImpl(
            StockLevelRepository stockLevelRepository,
            StockReservationRepository stockReservationRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            LedgerService ledgerService
    ) {
        this.stockLevelRepository = stockLevelRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.ledgerService = ledgerService;
    }

    @Override
    public List<StockReservation> reserveStock(Long orderId, String orderNumber, Long warehouseId, List<ReservationItem> items) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(items, "items must not be null");

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseId));

        // Coffman Condition elimination: Sort deterministically by productId ASC to avoid deadlocks
        List<ReservationItem> sortedItems = items.stream()
                .sorted(Comparator.comparing(ReservationItem::productId))
                .toList();

        List<StockReservation> createdReservations = new ArrayList<>();

        for (ReservationItem item : sortedItems) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ProductNotFoundException(item.productId()));

            // Pessimistic Write Lock acquisition with 3000ms timeout
            StockLevel stockLevel = stockLevelRepository
                    .findByProductAndWarehouseForUpdate(item.productId(), warehouseId)
                    .orElseThrow(() -> new InsufficientStockException(
                            product.getSku(), warehouse.getCode(), item.quantity(), 0));

            int available = stockLevel.getAvailableQty();
            if (available < item.quantity()) {
                log.warn("Insufficient stock for SKU '{}' at warehouse '{}': requested={}, available={}",
                        product.getSku(), warehouse.getCode(), item.quantity(), available);
                throw new InsufficientStockException(
                        product.getSku(), warehouse.getCode(), item.quantity(), available);
            }

            stockLevel.increaseReservation(item.quantity());
            StockLevel updatedStock = stockLevelRepository.save(stockLevel);

            StockReservation reservation = new StockReservation(updatedStock, orderId, item.quantity());
            StockReservation savedReservation = stockReservationRepository.save(reservation);
            createdReservations.add(savedReservation);

            ledgerService.recordMovement(
                    item.productId(),
                    warehouseId,
                    orderId,
                    MovementType.RESERVATION,
                    -item.quantity(),
                    updatedStock.getTotalQty(),
                    updatedStock.getReservedQty(),
                    "Reserva por confirmación de Orden " + orderNumber,
                    "SYSTEM"
            );
        }

        return createdReservations;
    }

    @Override
    public void releaseStock(Long orderId, String orderNumber) {
        Objects.requireNonNull(orderId, "orderId must not be null");

        List<StockReservation> activeReservations = stockReservationRepository
                .findByOrderIdAndStatusWithDetails(orderId, ReservationStatus.ACTIVE);

        // Sort by productId ASC to maintain deterministic locking order
        List<StockReservation> sortedReservations = activeReservations.stream()
                .sorted(Comparator.comparing(r -> r.getStockLevel().getProduct().getId()))
                .toList();

        for (StockReservation reservation : sortedReservations) {
            Long productId = reservation.getStockLevel().getProduct().getId();
            Long warehouseId = reservation.getStockLevel().getWarehouse().getId();

            StockLevel stockLevel = stockLevelRepository
                    .findByProductAndWarehouseForUpdate(productId, warehouseId)
                    .orElseThrow(() -> new IllegalStateException(
                            "StockLevel not found during release for product " + productId));

            stockLevel.releaseReservation(reservation.getQuantity());
            StockLevel updatedStock = stockLevelRepository.save(stockLevel);

            reservation.release();
            stockReservationRepository.save(reservation);

            ledgerService.recordMovement(
                    productId,
                    warehouseId,
                    orderId,
                    MovementType.RELEASE,
                    reservation.getQuantity(),
                    updatedStock.getTotalQty(),
                    updatedStock.getReservedQty(),
                    "Liberación por cancelación de Orden " + orderNumber,
                    "SYSTEM"
            );
        }
    }

    @Override
    public void fulfillStock(Long orderId, String orderNumber) {
        Objects.requireNonNull(orderId, "orderId must not be null");

        List<StockReservation> activeReservations = stockReservationRepository
                .findByOrderIdAndStatusWithDetails(orderId, ReservationStatus.ACTIVE);

        // Sort by productId ASC
        List<StockReservation> sortedReservations = activeReservations.stream()
                .sorted(Comparator.comparing(r -> r.getStockLevel().getProduct().getId()))
                .toList();

        for (StockReservation reservation : sortedReservations) {
            Long productId = reservation.getStockLevel().getProduct().getId();
            Long warehouseId = reservation.getStockLevel().getWarehouse().getId();

            StockLevel stockLevel = stockLevelRepository
                    .findByProductAndWarehouseForUpdate(productId, warehouseId)
                    .orElseThrow(() -> new IllegalStateException(
                            "StockLevel not found during fulfillment for product " + productId));

            stockLevel.fulfillReservation(reservation.getQuantity());
            StockLevel updatedStock = stockLevelRepository.save(stockLevel);

            reservation.fulfill();
            stockReservationRepository.save(reservation);

            ledgerService.recordMovement(
                    productId,
                    warehouseId,
                    orderId,
                    MovementType.FULFILLMENT,
                    -reservation.getQuantity(),
                    updatedStock.getTotalQty(),
                    updatedStock.getReservedQty(),
                    "Despacho físico (Fulfillment) de Orden " + orderNumber,
                    "SYSTEM"
            );
        }
    }
}
