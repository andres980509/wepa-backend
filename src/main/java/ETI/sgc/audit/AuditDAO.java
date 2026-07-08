package ETI.sgc.audit;

import org.jdbi.v3.core.Jdbi;

public class AuditDAO {
    private final Jdbi jdbi;

    public AuditDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void insert(
            String usuario,
            String ip,
            String accion,
            String modulo,
            String entidad,
            String entidadId,
            String beforeData,
            String afterData
    ) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO audit_logs (
                    usuario, ip, accion, modulo, entidad, entidad_id,
                    before_data, after_data, created_at
                )
                VALUES (
                    :usuario, :ip, :accion, :modulo, :entidad, :entidadId,
                    CAST(:beforeData AS jsonb), CAST(:afterData AS jsonb), NOW()
                )
                """)
                .bind("usuario", usuario)
                .bind("ip", ip)
                .bind("accion", accion)
                .bind("modulo", modulo)
                .bind("entidad", entidad)
                .bind("entidadId", entidadId)
                .bind("beforeData", normalizeJson(beforeData))
                .bind("afterData", normalizeJson(afterData))
                .execute());
    }

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        return value;
    }
}
