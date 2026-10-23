package com.stockpilot.inventory.service;

import com.stockpilot.inventory.dto.WarehouseCreateRequest;
import com.stockpilot.inventory.dto.WarehouseResponse;

import java.util.List;

public interface WarehouseService {

    List<WarehouseResponse> getAllActiveWarehouses();

    WarehouseResponse getWarehouseById(Long id);

    WarehouseResponse createWarehouse(WarehouseCreateRequest request);
}
