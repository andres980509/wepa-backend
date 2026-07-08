ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS integrante_id bigint;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'usuarios_integrante_id_fkey'
    ) THEN
        ALTER TABLE usuarios
            ADD CONSTRAINT usuarios_integrante_id_fkey
            FOREIGN KEY (integrante_id)
            REFERENCES integrantes(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_usuarios_integrante_unico
    ON usuarios (integrante_id)
    WHERE integrante_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_usuarios_rol_integrante
    ON usuarios (rol, integrante_id, activo);
