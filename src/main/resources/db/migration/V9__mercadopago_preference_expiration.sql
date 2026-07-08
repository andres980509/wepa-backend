ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS provider_preference_id varchar(180),
    ADD COLUMN IF NOT EXISTS expires_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_payment_transactions_provider_preference
    ON payment_transactions (provider, provider_preference_id)
    WHERE provider_preference_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_transactions_pending_expiration
    ON payment_transactions (status, expires_at)
    WHERE status = 'PENDIENTE';
