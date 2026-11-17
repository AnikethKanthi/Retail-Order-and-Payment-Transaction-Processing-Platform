CREATE TABLE order_service.customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT customers_email_ck CHECK (email = btrim(email) AND email <> ''),
    CONSTRAINT customers_name_ck CHECK (btrim(display_name) <> ''),
    CONSTRAINT customers_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT customers_version_ck CHECK (version >= 0),
    CONSTRAINT customers_timestamps_ck CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX customers_email_uq ON order_service.customers (lower(email));

CREATE TABLE order_service.orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(40) NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    currency VARCHAR(3) NOT NULL,
    subtotal NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    shipping_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_amount NUMERIC(19,4) GENERATED ALWAYS AS
        (subtotal - discount_amount + tax_amount + shipping_amount) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT orders_customer_fk FOREIGN KEY (customer_id)
        REFERENCES order_service.customers (id) ON DELETE RESTRICT,
    CONSTRAINT orders_number_ck CHECK (btrim(order_number) <> ''),
    CONSTRAINT orders_idempotency_ck CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT orders_status_ck CHECK (status IN
        ('PENDING', 'CONFIRMED', 'PAYMENT_FAILED', 'CANCELLED', 'FULFILLED')),
    CONSTRAINT orders_currency_ck CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT orders_amounts_ck CHECK (
        subtotal >= 0 AND subtotal <> 'NaN'::numeric
        AND discount_amount >= 0 AND discount_amount <= subtotal
        AND tax_amount >= 0 AND tax_amount <> 'NaN'::numeric
        AND shipping_amount >= 0 AND shipping_amount <> 'NaN'::numeric),
    CONSTRAINT orders_version_ck CHECK (version >= 0),
    CONSTRAINT orders_timestamps_ck CHECK (updated_at >= created_at)
);

CREATE INDEX orders_customer_created_idx ON order_service.orders (customer_id, created_at DESC);
CREATE INDEX orders_status_created_idx ON order_service.orders (status, created_at);

CREATE TABLE order_service.order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    line_total NUMERIC(19,4) GENERATED ALWAYS AS
        (quantity::numeric * unit_price - discount_amount + tax_amount) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT order_items_order_fk FOREIGN KEY (order_id)
        REFERENCES order_service.orders (id) ON DELETE RESTRICT,
    CONSTRAINT order_items_line_uq UNIQUE (order_id, line_number),
    CONSTRAINT order_items_line_ck CHECK (line_number > 0),
    CONSTRAINT order_items_sku_ck CHECK (sku = btrim(sku) AND sku <> ''),
    CONSTRAINT order_items_name_ck CHECK (btrim(product_name) <> ''),
    CONSTRAINT order_items_quantity_ck CHECK (quantity > 0),
    CONSTRAINT order_items_amounts_ck CHECK (
        unit_price >= 0 AND unit_price <> 'NaN'::numeric
        AND discount_amount >= 0 AND discount_amount <= quantity::numeric * unit_price
        AND tax_amount >= 0 AND tax_amount <> 'NaN'::numeric)
);
-- The (order_id, line_number) unique index also supports the order foreign key.
