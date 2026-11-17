CREATE TABLE payment_service.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- External reference: the order service owns the order, not this database schema.
    order_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(128),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT payments_provider_reference_uq UNIQUE (provider, provider_payment_id),
    CONSTRAINT payments_id_currency_uq UNIQUE (id, currency),
    CONSTRAINT payments_idempotency_ck CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT payments_provider_ck CHECK (provider = btrim(provider) AND provider <> ''),
    CONSTRAINT payments_provider_reference_ck CHECK
        (provider_payment_id IS NULL OR btrim(provider_payment_id) <> ''),
    CONSTRAINT payments_status_ck CHECK (status IN
        ('PENDING', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'CANCELLED', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    CONSTRAINT payments_currency_ck CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT payments_amount_ck CHECK (amount > 0 AND amount <> 'NaN'::numeric),
    CONSTRAINT payments_version_ck CHECK (version >= 0),
    CONSTRAINT payments_timestamps_ck CHECK (updated_at >= created_at)
);

CREATE INDEX payments_order_created_idx ON payment_service.payments (order_id, created_at DESC);
CREATE INDEX payments_status_updated_idx ON payment_service.payments (status, updated_at);

CREATE TABLE payment_service.transaction_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    provider_transaction_id VARCHAR(128),
    operation VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    correlation_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT transaction_records_payment_currency_fk FOREIGN KEY (payment_id, currency)
        REFERENCES payment_service.payments (id, currency) ON DELETE RESTRICT,
    CONSTRAINT transaction_records_idempotency_uq UNIQUE (payment_id, idempotency_key),
    CONSTRAINT transaction_records_provider_reference_uq UNIQUE (payment_id, provider_transaction_id),
    CONSTRAINT transaction_records_idempotency_ck CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT transaction_records_reference_ck CHECK
        (provider_transaction_id IS NULL OR btrim(provider_transaction_id) <> ''),
    CONSTRAINT transaction_records_operation_ck CHECK (operation IN
        ('AUTHORIZATION', 'CAPTURE', 'REFUND', 'VOID')),
    CONSTRAINT transaction_records_status_ck CHECK (status IN ('SUCCEEDED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT transaction_records_amount_ck CHECK (amount > 0 AND amount <> 'NaN'::numeric)
);

CREATE INDEX transaction_records_payment_time_idx
    ON payment_service.transaction_records (payment_id, occurred_at DESC);
CREATE INDEX transaction_records_correlation_idx
    ON payment_service.transaction_records (correlation_id) WHERE correlation_id IS NOT NULL;

-- Financial operation observations are append-only, including for the migration owner.
CREATE FUNCTION payment_service.reject_transaction_record_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'transaction_records is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER transaction_records_append_only
BEFORE UPDATE OR DELETE ON payment_service.transaction_records
FOR EACH ROW EXECUTE FUNCTION payment_service.reject_transaction_record_mutation();
