export interface Warehouse {
  id: number;
  name: string;
  code: string;
  address: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface StockLevel {
  id: number;
  productId: number;
  productSku: string;
  productName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  totalQty: number;
  reservedQty: number;
  availableQty: number; // Generated STORED column
  updatedAt: string;
}

export interface StockAdjustmentRequest {
  productId: number;
  warehouseId: number;
  deltaQty: number;
  movementType: 'INBOUND' | 'PHYSICAL_COUNT' | 'SCRAP' | 'ADJUSTMENT';
  reason: string;
  performedBy: string;
}

export interface LedgerEntry {
  id: number;
  productId: number;
  productSku: string;
  warehouseId: number;
  warehouseCode: string;
  deltaQty: number;
  movementType: 'INBOUND' | 'PHYSICAL_COUNT' | 'SCRAP' | 'RESERVATION' | 'RELEASE' | 'FULFILLMENT' | 'ADJUSTMENT';
  reason: string;
  performedBy: string;
  referenceId?: string;
  createdAt: string;
}
