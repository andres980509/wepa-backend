CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE documentos
    ADD COLUMN IF NOT EXISTS folder_path varchar(255),
    ADD COLUMN IF NOT EXISTS original_filename varchar(255),
    ADD COLUMN IF NOT EXISTS mime_type varchar(120),
    ADD COLUMN IF NOT EXISTS file_size_bytes bigint,
    ADD COLUMN IF NOT EXISTS checksum_sha256 varchar(64),
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS active boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS uploaded_by varchar(120);

UPDATE documentos
SET original_filename = COALESCE(original_filename, nombre_archivo),
    mime_type = COALESCE(mime_type,
        CASE
            WHEN lower(nombre_archivo) LIKE '%.pdf' THEN 'application/pdf'
            WHEN lower(nombre_archivo) LIKE '%.png' THEN 'image/png'
            WHEN lower(nombre_archivo) LIKE '%.jpg' OR lower(nombre_archivo) LIKE '%.jpeg' THEN 'image/jpeg'
            ELSE 'application/octet-stream'
        END
    )
WHERE original_filename IS NULL OR mime_type IS NULL;

CREATE TABLE IF NOT EXISTS payment_transactions (
    id bigserial PRIMARY KEY,
    provider varchar(20) NOT NULL CHECK (provider IN ('WOMPI', 'EPAYCO')),
    provider_reference varchar(180) UNIQUE,
    provider_status varchar(80),
    provider_message text,
    checkout_url text,
    obligacion_id bigint NOT NULL REFERENCES obligaciones(id),
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    currency varchar(3) NOT NULL DEFAULT 'COP',
    method varchar(40),
    payer_email varchar(180),
    payer_document varchar(80),
    status varchar(20) NOT NULL CHECK (status IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'EXPIRADO')),
    raw_request jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT NOW(),
    updated_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payment_events (
    id bigserial PRIMARY KEY,
    provider varchar(20) NOT NULL CHECK (provider IN ('WOMPI', 'EPAYCO')),
    provider_reference varchar(180),
    event_type varchar(80) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mobile_refresh_tokens (
    id bigserial PRIMARY KEY,
    usuario_id bigint NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    device_name varchar(180),
    expires_at timestamptz NOT NULL,
    revoked boolean NOT NULL DEFAULT false,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_obligacion
    ON payment_transactions (obligacion_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_status
    ON payment_transactions (status, provider, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_events_reference
    ON payment_events (provider_reference, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_mobile_refresh_tokens_usuario
    ON mobile_refresh_tokens (usuario_id, revoked, expires_at);

CREATE INDEX IF NOT EXISTS idx_documentos_entidad_version
    ON documentos (entidad_tipo, entidad_id, tipo_documento, version DESC);

CREATE INDEX IF NOT EXISTS idx_obligaciones_estado_tipo_saldo
    ON obligaciones (estado, tipo_tercero, saldo, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_movimientos_fecha_tipo
    ON movimientos (created_at DESC, tipo);

CREATE INDEX IF NOT EXISTS idx_integrantes_nombre_trgm
    ON integrantes USING gin (nombre_completo gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_proveedores_nombre_trgm
    ON proveedores USING gin (nombre gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_proveedores_razon_social_trgm
    ON proveedores USING gin (razon_social gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_entidades_nombre_trgm
    ON entidades USING gin (nombre gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_patrocinadores_nombre_trgm
    ON patrocinadores USING gin (nombre gin_trgm_ops);
