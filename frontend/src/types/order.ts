export type OrderStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED' | 'FULFILLED';

export interface OrderLine {
  id: number;
  productId: number;
  productSku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  id: number;
  orderNumber: string;
  idempotencyKey: string;
  status: OrderStatus;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  totalNetAmount: number;
  notes?: string;
  version: number;
  lines: OrderLine[];
  createdAt: string;
  updatedAt: string;
}

export interface OrderItemRequest {
  productId: number;
  quantity: number;
}

export interface CreateOrderRequest {
  warehouseId: number;
  notes?: string;
  items: OrderItemRequest[];
}

export interface ApiResponseWithIdempotency<T> {
  data: T;
  isIdempotentReplay: boolean;
  idempotencyKey: string;
}
