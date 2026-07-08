ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'CEDULA';
ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'CONTRATO';
ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'EPS';
ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'ARL';
ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'FACTURA';
ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'SOPORTE';
ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'RUT';
ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'CAMARA_COMERCIO';
ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'SOPORTE_EXTERNO';
ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'RECIBO_SISTEMA';
ALTER TYPE tipo_documento_enum ADD VALUE IF NOT EXISTS 'OTRO';

ALTER TYPE entidad_tipo_enum ADD VALUE IF NOT EXISTS 'USUARIO';
ALTER TYPE entidad_tipo_enum ADD VALUE IF NOT EXISTS 'PROVEEDOR';
ALTER TYPE entidad_tipo_enum ADD VALUE IF NOT EXISTS 'PATROCINADOR';
ALTER TYPE entidad_tipo_enum ADD VALUE IF NOT EXISTS 'ENTIDAD';
ALTER TYPE entidad_tipo_enum ADD VALUE IF NOT EXISTS 'OBLIGACION';
ALTER TYPE entidad_tipo_enum ADD VALUE IF NOT EXISTS 'MOVIMIENTO_FINANCIERO';

CREATE TABLE IF NOT EXISTS document_types (
    id bigserial PRIMARY KEY,
    codigo varchar(60) NOT NULL UNIQUE,
    nombre varchar(160) NOT NULL,
    descripcion text,
    entidad_tipo varchar(60),
    obligatorio boolean NOT NULL DEFAULT false,
    vigencia_dias integer,
    activo boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT NOW()
);

INSERT INTO document_types (codigo, nombre, descripcion, entidad_tipo, obligatorio, vigencia_dias)
VALUES
    ('CEDULA', 'Cedula', 'Documento de identificacion personal', 'INTEGRANTE', true, NULL),
    ('CONTRATO', 'Contrato', 'Contrato o acuerdo firmado', 'INTEGRANTE', true, 365),
    ('EPS', 'EPS', 'Soporte de afiliacion EPS', 'INTEGRANTE', true, 180),
    ('ARL', 'ARL', 'Soporte de afiliacion ARL', 'INTEGRANTE', true, 180),
    ('FACTURA', 'Factura', 'Factura o cuenta de cobro', 'PROVEEDOR', false, NULL),
    ('SOPORTE', 'Soporte', 'Soporte documental general', NULL, false, NULL),
    ('RUT', 'RUT', 'Registro unico tributario', 'PROVEEDOR', true, 365),
    ('CAMARA_COMERCIO', 'Camara de comercio', 'Certificado mercantil vigente', 'PROVEEDOR', false, 90)
ON CONFLICT (codigo) DO UPDATE
SET nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    entidad_tipo = EXCLUDED.entidad_tipo,
    obligatorio = EXCLUDED.obligatorio,
    vigencia_dias = EXCLUDED.vigencia_dias;

ALTER TABLE documentos
    ADD COLUMN IF NOT EXISTS estado varchar(20) NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN IF NOT EXISTS fecha_vencimiento date,
    ADD COLUMN IF NOT EXISTS validado_por varchar(120),
    ADD COLUMN IF NOT EXISTS validado_en timestamptz,
    ADD COLUMN IF NOT EXISTS observaciones text,
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz,
    ADD COLUMN IF NOT EXISTS deleted_by varchar(120),
    ADD COLUMN IF NOT EXISTS metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'documentos_estado_check'
    ) THEN
        ALTER TABLE documentos
            ADD CONSTRAINT documentos_estado_check
            CHECK (estado IN ('PENDIENTE', 'VALIDADO', 'RECHAZADO', 'VENCIDO'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS audit_logs (
    id bigserial PRIMARY KEY,
    usuario varchar(120),
    ip varchar(80),
    accion varchar(80) NOT NULL,
    modulo varchar(80) NOT NULL,
    entidad varchar(120),
    entidad_id varchar(120),
    before_data jsonb,
    after_data jsonb,
    created_at timestamptz NOT NULL DEFAULT NOW()
);

DO $$
DECLARE
    c record;
BEGIN
    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'payment_transactions'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%provider%'
    LOOP
        EXECUTE format('ALTER TABLE payment_transactions DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE payment_transactions
    ADD CONSTRAINT payment_transactions_provider_check
    CHECK (provider IN ('WOMPI', 'EPAYCO', 'MERCADOPAGO'));

DO $$
DECLARE
    c record;
BEGIN
    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'payment_events'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%provider%'
    LOOP
        EXECUTE format('ALTER TABLE payment_events DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE payment_events
    ADD CONSTRAINT payment_events_provider_check
    CHECK (provider IN ('WOMPI', 'EPAYCO', 'MERCADOPAGO'));

CREATE UNIQUE INDEX IF NOT EXISTS idx_documentos_entidad_tipo_folder_version
    ON documentos (entidad_tipo, entidad_id, tipo_documento, (COALESCE(folder_path, '')), version)
    WHERE COALESCE(active, true) = true AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_documentos_search
    ON documentos (estado, entidad_tipo, tipo_documento, fecha_vencimiento, created_at DESC)
    WHERE COALESCE(active, true) = true AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_documentos_nombre_trgm
    ON documentos USING gin (original_filename gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_document_types_entidad
    ON document_types (entidad_tipo, activo, obligatorio);

CREATE INDEX IF NOT EXISTS idx_audit_logs_modulo_entidad
    ON audit_logs (modulo, entidad, entidad_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_usuario
    ON audit_logs (usuario, created_at DESC);
