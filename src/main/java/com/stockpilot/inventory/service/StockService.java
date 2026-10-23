package com.stockpilot.inventory.service;

import com.stockpilot.inventory.dto.StockAdjustmentRequest;
import com.stockpilot.inventory.dto.StockLevelResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StockService {

    StockLevelResponse adjustStock(StockAdjustmentRequest request);

    List<StockLevelResponse> getStockByProduct(Long productId);

    Page<StockLevelResponse> getStockByWarehouse(Long warehouseId, Pageable pageable);
}
