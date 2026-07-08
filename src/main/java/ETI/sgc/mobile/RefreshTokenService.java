package ETI.sgc.mobile;

import ETI.sgc.config.AppConfig;
import ETI.sgc.error.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

public class RefreshTokenService {
    private final RefreshTokenDAO refreshTokenDAO;
    private final long ttlDays;

    public RefreshTokenService(RefreshTokenDAO refreshTokenDAO, AppConfig config) {
        this.refreshTokenDAO = refreshTokenDAO;
        this.ttlDays = config.getLong("JWT_REFRESH_TTL_DAYS", 30);
    }

    public String create(Long usuarioId, String deviceName) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        refreshTokenDAO.create(usuarioId, hash(token), OffsetDateTime.now().plusDays(ttlDays), deviceName);
        return token;
    }

    public Map<String, Object> validate(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(400, "Refresh token requerido");
        }

        Map<String, Object> row = refreshTokenDAO.findActive(hash(token));
        if (row == null) {
            throw new ApiException(401, "Refresh token invalido o expirado");
        }
        return row;
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            refreshTokenDAO.revoke(hash(token));
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ApiException(500, "No se pudo procesar token");
        }
    }
}
