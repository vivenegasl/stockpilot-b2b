package com.stockpilot.inventory.service;

import com.stockpilot.inventory.domain.Warehouse;
import com.stockpilot.inventory.dto.WarehouseCreateRequest;
import com.stockpilot.inventory.dto.WarehouseResponse;
import com.stockpilot.inventory.exception.WarehouseNotFoundException;
import com.stockpilot.inventory.repository.WarehouseRepository;
import com.stockpilot.shared.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class WarehouseServiceImpl implements WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseServiceImpl.class);

    private final WarehouseRepository warehouseRepository;

    public WarehouseServiceImpl(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Override
    public List<WarehouseResponse> getAllActiveWarehouses() {
        return warehouseRepository.findAll().stream()
                .filter(Warehouse::isActive)
                .map(WarehouseResponse::from)
                .toList();
    }

    @Override
    public WarehouseResponse getWarehouseById(Long id) {
        return warehouseRepository.findById(id)
                .map(WarehouseResponse::from)
                .orElseThrow(() -> new WarehouseNotFoundException(id));
    }

    @Override
    @Transactional
    public WarehouseResponse createWarehouse(WarehouseCreateRequest request) {
        Objects.requireNonNull(request, "WarehouseCreateRequest must not be null");

        String code = request.code().trim().toUpperCase();
        if (warehouseRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException(String.format("Warehouse code '%s' already exists.", code), "duplicate-warehouse-code");
        }

        Warehouse warehouse = new Warehouse(code, request.name(), request.address());
        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Registered new warehouse: id={}, code={}", saved.getId(), saved.getCode());
        return WarehouseResponse.from(saved);
    }
}
