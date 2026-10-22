-- Indices de Búsqueda Rápida y Llaves Naturales
CREATE INDEX idx_product_category ON product (category_id);
CREATE INDEX idx_stock_level_available ON stock_level (warehouse_id, available_qty) WHERE available_qty > 0;
CREATE INDEX idx_order_warehouse_status ON purchase_order (warehouse_id, status);
CREATE INDEX idx_order_created_at ON purchase_order (created_at DESC);
CREATE INDEX idx_order_line_order ON order_line (order_id);

-- Indice parcial para reservas activas (optimizacion de scheduler y cancelaciones)
CREATE INDEX idx_stock_reservation_active ON stock_reservation (stock_level_id, status) WHERE status = 'ACTIVE';

-- Indice para auditoria cronologica de movimientos por producto y bodega
CREATE INDEX idx_inventory_ledger_audit ON inventory_ledger (product_id, warehouse_id, created_at DESC);

-- Indice para purga de registros de idempotencia expirados
CREATE INDEX idx_idempotency_expires ON idempotency_record (expires_at);
