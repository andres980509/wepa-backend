CREATE TABLE IF NOT EXISTS mobile_push_tokens (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    integrante_id BIGINT NOT NULL REFERENCES integrantes(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL DEFAULT 'FCM',
    device_name VARCHAR(120),
    app_version VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mobile_push_tokens_usuario
    ON mobile_push_tokens(usuario_id, active);

CREATE INDEX IF NOT EXISTS idx_mobile_push_tokens_integrante
    ON mobile_push_tokens(integrante_id, active);
