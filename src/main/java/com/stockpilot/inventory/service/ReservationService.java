package com.stockpilot.inventory.service;

import com.stockpilot.inventory.domain.StockReservation;
import com.stockpilot.inventory.dto.ReservationItem;

import java.util.List;

public interface ReservationService {

    List<StockReservation> reserveStock(Long orderId, String orderNumber, Long warehouseId, List<ReservationItem> items);

    void releaseStock(Long orderId, String orderNumber);

    void fulfillStock(Long orderId, String orderNumber);
}
