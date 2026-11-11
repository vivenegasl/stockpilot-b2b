import { requestJson, requestWithIdempotency } from './client';
import type { ApiResponseWithIdempotency, CreateOrderRequest, Order, OrderStatus } from '../types/order';
import type { PageResponse } from '../types/catalog';

export const orderApi = {
  getOrders: (status?: OrderStatus, page = 0, size = 15) => {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sort: 'createdAt,desc',
    });
    if (status) params.set('status', status);

    return requestJson<PageResponse<Order>>(`/orders?${params.toString()}`);
  },

  getOrderById: (id: number) => {
    return requestJson<Order>(`/orders/${id}`);
  },

  createOrderWithIdempotency: (data: CreateOrderRequest, idempotencyKey?: string): Promise<ApiResponseWithIdempotency<Order>> => {
    return requestWithIdempotency<Order>('/orders', 'POST', data, idempotencyKey);
  },

  confirmOrder: (id: number) => {
    return requestJson<Order>(`/orders/${id}/confirm`, {
      method: 'PUT',
    });
  },

  cancelOrder: (id: number) => {
    return requestJson<Order>(`/orders/${id}/cancel`, {
      method: 'PUT',
    });
  },

  fulfillOrder: (id: number) => {
    return requestJson<Order>(`/orders/${id}/fulfill`, {
      method: 'PUT',
    });
  },
};
