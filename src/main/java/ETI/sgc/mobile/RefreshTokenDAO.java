package ETI.sgc.mobile;

import org.jdbi.v3.core.Jdbi;

import java.time.OffsetDateTime;
import java.util.Map;

public class RefreshTokenDAO {
    private final Jdbi jdbi;

    public RefreshTokenDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void create(Long usuarioId, String tokenHash, OffsetDateTime expiresAt, String deviceName) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO mobile_refresh_tokens (usuario_id, token_hash, expires_at, device_name, revoked, created_at)
                VALUES (:usuarioId, :tokenHash, :expiresAt, :deviceName, false, NOW())
                """)
                .bind("usuarioId", usuarioId)
                .bind("tokenHash", tokenHash)
                .bind("expiresAt", expiresAt)
                .bind("deviceName", deviceName)
                .execute());
    }

    public Map<String, Object> findActive(String tokenHash) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT *
                FROM mobile_refresh_tokens
                WHERE token_hash = :tokenHash
                  AND revoked = false
                  AND expires_at > NOW()
                """)
                .bind("tokenHash", tokenHash)
                .mapToMap()
                .findOne()
                .orElse(null));
    }

    public void revoke(String tokenHash) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE mobile_refresh_tokens
                SET revoked = true, revoked_at = NOW()
                WHERE token_hash = :tokenHash
                """)
                .bind("tokenHash", tokenHash)
                .execute());
    }
}
