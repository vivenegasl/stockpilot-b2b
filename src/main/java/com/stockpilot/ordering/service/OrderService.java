package com.stockpilot.ordering.service;

import com.stockpilot.ordering.dto.CreateOrderRequest;
import com.stockpilot.ordering.dto.OrderResponse;

import java.util.UUID;

public interface OrderService {

    OrderResponse createDraftOrder(CreateOrderRequest request, UUID idempotencyKey);

    OrderResponse confirmOrder(Long id);

    OrderResponse cancelOrder(Long id);

    OrderResponse fulfillOrder(Long id);

    OrderResponse getOrderById(Long id);

    OrderResponse getOrderByNumber(String orderNumber);
}
