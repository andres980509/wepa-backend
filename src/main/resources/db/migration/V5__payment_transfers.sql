CREATE TABLE IF NOT EXISTS payment_transfers (
    id BIGSERIAL PRIMARY KEY,
    obligacion_id BIGINT NOT NULL REFERENCES obligaciones(id),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    integrante_id BIGINT NOT NULL REFERENCES integrantes(id),
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'COP',
    method VARCHAR(80) NOT NULL,
    bank VARCHAR(120),
    reference VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    support_path TEXT NOT NULL,
    support_original_filename TEXT NOT NULL,
    support_mime_type VARCHAR(120) NOT NULL,
    support_size_bytes BIGINT NOT NULL,
    receipt_number VARCHAR(60) UNIQUE,
    receipt_path TEXT,
    receipt_mime_type VARCHAR(120) DEFAULT 'application/pdf',
    observations TEXT,
    rejection_reason TEXT,
    movement_id BIGINT REFERENCES movimientos(id),
    reviewed_by VARCHAR(120),
    reviewed_at TIMESTAMP,
    submitted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT payment_transfers_status_check
        CHECK (status IN ('PENDING_VERIFICATION', 'APPROVED', 'REJECTED')),
    CONSTRAINT payment_transfers_amount_check
        CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_transfers_status
    ON payment_transfers(status, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_transfers_integrante
    ON payment_transfers(integrante_id, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_transfers_obligacion
    ON payment_transfers(obligacion_id, submitted_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_transfers_reference_active
    ON payment_transfers(integrante_id, reference)
    WHERE status IN ('PENDING_VERIFICATION', 'APPROVED');

CREATE TABLE IF NOT EXISTS payment_transfer_events (
    id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NOT NULL REFERENCES payment_transfers(id) ON DELETE CASCADE,
    action VARCHAR(40) NOT NULL,
    username VARCHAR(120),
    observations TEXT,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_transfer_events_transfer
    ON payment_transfer_events(transfer_id, created_at DESC);
