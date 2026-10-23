package com.stockpilot.inventory.service;

import com.stockpilot.audit.domain.MovementType;
import com.stockpilot.audit.service.LedgerService;
import com.stockpilot.catalog.domain.Product;
import com.stockpilot.catalog.exception.ProductNotFoundException;
import com.stockpilot.catalog.repository.ProductRepository;
import com.stockpilot.inventory.domain.StockLevel;
import com.stockpilot.inventory.domain.Warehouse;
import com.stockpilot.inventory.dto.StockAdjustmentRequest;
import com.stockpilot.inventory.dto.StockLevelResponse;
import com.stockpilot.inventory.exception.InvalidStockAdjustmentException;
import com.stockpilot.inventory.exception.WarehouseNotFoundException;
import com.stockpilot.inventory.repository.StockLevelRepository;
import com.stockpilot.inventory.repository.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class StockServiceImpl implements StockService {

    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

    private final StockLevelRepository stockLevelRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final LedgerService ledgerService;

    public StockServiceImpl(
            StockLevelRepository stockLevelRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            LedgerService ledgerService
    ) {
        this.stockLevelRepository = stockLevelRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.ledgerService = ledgerService;
    }

    @Override
    @Transactional
    public StockLevelResponse adjustStock(StockAdjustmentRequest request) {
        Objects.requireNonNull(request, "StockAdjustmentRequest must not be null");

        if (request.quantityDelta() == 0) {
            throw new InvalidStockAdjustmentException("Quantity delta cannot be zero.");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new WarehouseNotFoundException(request.warehouseId()));

        // Pessimistic Write Lock acquisition with 3000ms timeout
        StockLevel stockLevel = stockLevelRepository
                .findByProductAndWarehouseForUpdate(product.getId(), warehouse.getId())
                .orElseGet(() -> {
                    if (request.quantityDelta() < 0) {
                        throw new InvalidStockAdjustmentException("Cannot perform negative adjustment on non-existent stock.");
                    }
                    return new StockLevel(product, warehouse, 0);
                });

        int currentTotal = stockLevel.getTotalQty();
        int reserved = stockLevel.getReservedQty();
        int delta = request.quantityDelta();

        if (currentTotal + delta < reserved) {
            log.warn("Invalid stock reduction rejected: currentTotal={}, reserved={}, requestedDelta={}",
                    currentTotal, reserved, delta);
            throw new InvalidStockAdjustmentException(currentTotal, reserved, delta);
        }

        stockLevel.adjustTotalQty(delta);
        StockLevel savedStock = stockLevelRepository.save(stockLevel);

        MovementType movementType = delta > 0 ? MovementType.INBOUND : MovementType.ADJUSTMENT;

        // Immutable audit trail recording within the same ACID transaction
        ledgerService.recordMovement(
                product.getId(),
                warehouse.getId(),
                null,
                movementType,
                delta,
                savedStock.getTotalQty(),
                savedStock.getReservedQty(),
                request.reason(),
                request.performedBy()
        );

        log.info("Stock adjusted successfully: productId={}, warehouseId={}, delta={}, resultingTotal={}, available={}",
                product.getId(), warehouse.getId(), delta, savedStock.getTotalQty(), savedStock.getAvailableQty());

        return StockLevelResponse.from(savedStock);
    }

    @Override
    public List<StockLevelResponse> getStockByProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        return stockLevelRepository.findByProductIdWithDetails(productId).stream()
                .map(StockLevelResponse::from)
                .toList();
    }

    @Override
    public Page<StockLevelResponse> getStockByWarehouse(Long warehouseId, Pageable pageable) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new WarehouseNotFoundException(warehouseId);
        }
        return stockLevelRepository.findByWarehouseIdWithDetails(warehouseId, pageable)
                .map(StockLevelResponse::from);
    }
}
