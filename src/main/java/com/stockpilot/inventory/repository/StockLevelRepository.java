package com.stockpilot.inventory.repository;

import com.stockpilot.inventory.domain.StockLevel;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT sl FROM StockLevel sl WHERE sl.product.id = :productId AND sl.warehouse.id = :warehouseId")
    Optional<StockLevel> findByProductAndWarehouseForUpdate(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId
    );

    Optional<StockLevel> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    @Query("SELECT sl FROM StockLevel sl " +
            "JOIN FETCH sl.warehouse w " +
            "JOIN FETCH sl.product p " +
            "WHERE p.id = :productId AND w.active = true")
    List<StockLevel> findByProductIdWithDetails(@Param("productId") Long productId);

    @Query(value = "SELECT sl FROM StockLevel sl " +
            "JOIN FETCH sl.product p " +
            "JOIN FETCH sl.warehouse w " +
            "WHERE w.id = :warehouseId",
            countQuery = "SELECT COUNT(sl) FROM StockLevel sl WHERE sl.warehouse.id = :warehouseId")
    Page<StockLevel> findByWarehouseIdWithDetails(@Param("warehouseId") Long warehouseId, Pageable pageable);
}
