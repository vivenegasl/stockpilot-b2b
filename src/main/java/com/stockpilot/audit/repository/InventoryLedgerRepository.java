package com.stockpilot.audit.repository;

import com.stockpilot.audit.domain.InventoryLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, Long> {

    @Query("SELECT l FROM InventoryLedger l " +
            "WHERE (:productId IS NULL OR l.productId = :productId) " +
            "AND (:warehouseId IS NULL OR l.warehouseId = :warehouseId) " +
            "ORDER BY l.createdAt DESC")
    Page<InventoryLedger> findLedgerEntries(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId,
            Pageable pageable
    );

    long countByProductId(Long productId);
}
