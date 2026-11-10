package com.stockpilot.inventory.repository;

import com.stockpilot.inventory.domain.ReservationStatus;
import com.stockpilot.inventory.domain.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderId(Long orderId);

    @Query("SELECT r FROM StockReservation r " +
            "JOIN FETCH r.stockLevel sl " +
            "JOIN FETCH sl.product p " +
            "JOIN FETCH sl.warehouse w " +
            "WHERE r.orderId = :orderId AND r.status = :status")
    List<StockReservation> findByOrderIdAndStatusWithDetails(
            @Param("orderId") Long orderId,
            @Param("status") ReservationStatus status
    );
}
