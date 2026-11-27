CREATE TABLE inventory_service.reservations (
    id UUID PRIMARY KEY,
    inventory_id UUID NOT NULL REFERENCES inventory_service.inventory (id) ON DELETE RESTRICT,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    status VARCHAR(16) NOT NULL DEFAULT 'RESERVED' CHECK (status IN ('RESERVED', 'RELEASED', 'DEDUCTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (updated_at >= created_at)
);

CREATE INDEX inventory_reservations_inventory_idx ON inventory_service.reservations (inventory_id);
