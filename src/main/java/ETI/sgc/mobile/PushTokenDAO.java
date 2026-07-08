package ETI.sgc.mobile;

import org.jdbi.v3.core.Jdbi;

public class PushTokenDAO {
    private final Jdbi jdbi;

    public PushTokenDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void upsert(
            Long usuarioId,
            Long integranteId,
            String token,
            String platform,
            String provider,
            String deviceName,
            String appVersion
    ) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO mobile_push_tokens (
                    usuario_id, integrante_id, token, platform, provider,
                    device_name, app_version, active, last_seen_at, created_at, updated_at
                )
                VALUES (
                    :usuarioId, :integranteId, :token, :platform, :provider,
                    :deviceName, :appVersion, TRUE, NOW(), NOW(), NOW()
                )
                ON CONFLICT (token) DO UPDATE SET
                    usuario_id = EXCLUDED.usuario_id,
                    integrante_id = EXCLUDED.integrante_id,
                    platform = EXCLUDED.platform,
                    provider = EXCLUDED.provider,
                    device_name = EXCLUDED.device_name,
                    app_version = EXCLUDED.app_version,
                    active = TRUE,
                    last_seen_at = NOW(),
                    updated_at = NOW()
                """)
                .bind("usuarioId", usuarioId)
                .bind("integranteId", integranteId)
                .bind("token", token)
                .bind("platform", platform)
                .bind("provider", provider)
                .bind("deviceName", deviceName)
                .bind("appVersion", appVersion)
                .execute());
    }

    public void deactivate(Long usuarioId, String token) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE mobile_push_tokens
                SET active = FALSE, updated_at = NOW()
                WHERE usuario_id = :usuarioId AND token = :token
                """)
                .bind("usuarioId", usuarioId)
                .bind("token", token)
                .execute());
    }
}
