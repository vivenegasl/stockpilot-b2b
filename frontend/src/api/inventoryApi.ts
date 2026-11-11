import { requestJson } from './client';
import type { LedgerEntry, StockAdjustmentRequest, StockLevel, Warehouse } from '../types/inventory';
import type { PageResponse } from '../types/catalog';

export const inventoryApi = {
  getWarehouses: () => {
    return requestJson<Warehouse[]>('/warehouses');
  },

  getStockByProduct: (productId: number) => {
    return requestJson<StockLevel[]>(`/stock/product/${productId}`);
  },

  getStockByWarehouse: (warehouseId: number, page = 0, size = 20) => {
    return requestJson<PageResponse<StockLevel>>(`/stock/warehouse/${warehouseId}?page=${page}&size=${size}`);
  },

  adjustStock: (data: StockAdjustmentRequest) => {
    return requestJson<StockLevel>('/stock/adjust', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  getLedgerHistory: (page = 0, size = 25, productId?: number, warehouseId?: number) => {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });
    if (productId) params.set('productId', productId.toString());
    if (warehouseId) params.set('warehouseId', warehouseId.toString());

    return requestJson<PageResponse<LedgerEntry>>(`/inventory/ledger?${params.toString()}`);
  },
};
