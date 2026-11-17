CREATE TABLE inventory_service.inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(64) NOT NULL,
    warehouse_code VARCHAR(40) NOT NULL,
    on_hand_quantity BIGINT NOT NULL DEFAULT 0,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    available_quantity BIGINT GENERATED ALWAYS AS
        (on_hand_quantity - reserved_quantity) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT inventory_sku_warehouse_uq UNIQUE (sku, warehouse_code),
    CONSTRAINT inventory_sku_ck CHECK (sku = btrim(sku) AND sku <> ''),
    CONSTRAINT inventory_warehouse_ck CHECK
        (warehouse_code = btrim(warehouse_code) AND warehouse_code <> ''),
    CONSTRAINT inventory_quantities_ck CHECK (
        on_hand_quantity >= 0 AND reserved_quantity >= 0
        AND reserved_quantity <= on_hand_quantity),
    CONSTRAINT inventory_version_ck CHECK (version >= 0),
    CONSTRAINT inventory_timestamps_ck CHECK (updated_at >= created_at)
);
-- The (sku, warehouse_code) unique index supports both SKU and exact stock lookup.
CREATE INDEX inventory_warehouse_idx ON inventory_service.inventory (warehouse_code, sku);
