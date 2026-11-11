package com.stockpilot.ordering.repository;

import com.stockpilot.ordering.domain.OrderStatus;
import com.stockpilot.ordering.domain.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByIdempotencyKey(UUID idempotencyKey);

    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);

    @Query("SELECT po FROM PurchaseOrder po " +
            "JOIN FETCH po.warehouse w " +
            "LEFT JOIN FETCH po.lines l " +
            "LEFT JOIN FETCH l.product p " +
            "WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT po FROM PurchaseOrder po " +
            "JOIN FETCH po.warehouse w " +
            "LEFT JOIN FETCH po.lines l " +
            "LEFT JOIN FETCH l.product p " +
            "WHERE po.orderNumber = :orderNumber")
    Optional<PurchaseOrder> findByOrderNumberWithDetails(@Param("orderNumber") String orderNumber);

    @Query(value = "SELECT po FROM PurchaseOrder po JOIN FETCH po.warehouse w WHERE (:status IS NULL OR po.status = :status)",
           countQuery = "SELECT COUNT(po) FROM PurchaseOrder po WHERE (:status IS NULL OR po.status = :status)")
    Page<PurchaseOrder> findAllByStatusPaged(@Param("status") OrderStatus status, Pageable pageable);
}
