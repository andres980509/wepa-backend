package ETI.sgc.security;

import ETI.sgc.config.AppConfig;
import ETI.sgc.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    private static String secret = "CAMBIAR_ESTA_CLAVE_SUPER_LARGA_Y_SEGURA_123456";
    private static long accessTtlMs = 1000L * 60 * 60 * 8;
    private static long mobileAccessTtlMs = 1000L * 60 * 30;

    public static void configure(AppConfig config) {
        secret = config.get("JWT_SECRET", secret);
        accessTtlMs = config.getLong("JWT_ACCESS_TTL_MS", accessTtlMs);
        mobileAccessTtlMs = config.getLong("JWT_MOBILE_ACCESS_TTL_MS", mobileAccessTtlMs);

        if (secret.contains("CAMBIAR_ESTA_CLAVE")) {
            System.err.println("ADVERTENCIA: JWT_SECRET usa valor de desarrollo. Cambiar en staging/produccion.");
        }
    }

    public static String generar(Usuario u) {
        return generar(u, accessTtlMs, "web");
    }

    public static String generarMobile(Usuario u) {
        return generar(u, mobileAccessTtlMs, "mobile");
    }

    private static String generar(Usuario u, long ttlMs, String audience) {
        return Jwts.builder()
                .setSubject(u.username)
                .claim("rol", u.rol)
                .claim("uid", u.id)
                .claim("integrante_id", u.integrante_id)
                .claim("permisos", Rbac.permissionsForRole(u.rol))
                .setAudience(audience)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    public static Claims validar(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
