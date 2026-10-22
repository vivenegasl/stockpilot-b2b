CREATE TABLE stock_level (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    total_qty INTEGER NOT NULL CHECK (total_qty >= 0),
    reserved_qty INTEGER NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    available_qty INTEGER GENERATED ALWAYS AS (total_qty - reserved_qty) STORED,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_stock_level_product_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT chk_stock_reserved_within_total CHECK (reserved_qty <= total_qty)
);
