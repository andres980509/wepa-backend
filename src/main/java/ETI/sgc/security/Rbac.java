package ETI.sgc.security;

import ETI.sgc.error.ApiException;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Set;

public final class Rbac {
    public static final String DOCUMENTOS_VER = "documentos.ver";
    public static final String DOCUMENTOS_SUBIR = "documentos.subir";
    public static final String DOCUMENTOS_VALIDAR = "documentos.validar";
    public static final String PAGOS_CREAR = "pagos.crear";
    public static final String PAGOS_REVERSAR = "pagos.reversar";
    public static final String PAGOS_VALIDAR = "pagos.validar";
    public static final String FINANZAS_VER = "finanzas.ver";
    public static final String DASHBOARD_VER = "dashboard.ver";
    public static final String USUARIOS_GESTIONAR = "usuarios.gestionar";
    public static final String TERCEROS_VER = "terceros.ver";
    public static final String TERCEROS_GESTIONAR = "terceros.gestionar";
    public static final String CONFIGURACION_GESTIONAR = "configuracion.gestionar";
    public static final String NOTIFICACIONES_PUSH = "notificaciones.push";

    private static final Set<String> ALL = Set.of(
            DOCUMENTOS_VER,
            DOCUMENTOS_SUBIR,
            DOCUMENTOS_VALIDAR,
            PAGOS_CREAR,
            PAGOS_REVERSAR,
            PAGOS_VALIDAR,
            FINANZAS_VER,
            DASHBOARD_VER,
            USUARIOS_GESTIONAR,
            TERCEROS_VER,
            TERCEROS_GESTIONAR,
            CONFIGURACION_GESTIONAR,
            NOTIFICACIONES_PUSH
    );

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "ADMIN", ALL,
            "CONTADOR", Set.of(DOCUMENTOS_VER, DOCUMENTOS_SUBIR, PAGOS_CREAR, PAGOS_VALIDAR, FINANZAS_VER, DASHBOARD_VER, TERCEROS_VER),
            "TESORERO", Set.of(DOCUMENTOS_VER, PAGOS_CREAR, PAGOS_REVERSAR, PAGOS_VALIDAR, FINANZAS_VER, DASHBOARD_VER, TERCEROS_VER),
            "DOCENTE", Set.of(DOCUMENTOS_VER, DOCUMENTOS_SUBIR, DASHBOARD_VER, TERCEROS_VER),
            "INTEGRANTE", Set.of(DOCUMENTOS_VER, DOCUMENTOS_SUBIR),
            "AUDITOR", Set.of(DOCUMENTOS_VER, DOCUMENTOS_VALIDAR, FINANZAS_VER, DASHBOARD_VER, TERCEROS_VER),
            "VENDEDOR", Set.of(DASHBOARD_VER, TERCEROS_VER)
    );

    private Rbac() {
    }

    public static Set<String> permissionsForRole(String role) {
        if (role == null) {
            return Set.of();
        }
        return ROLE_PERMISSIONS.getOrDefault(role.trim().toUpperCase(), Set.of());
    }

    public static boolean hasPermission(String role, String permission) {
        return permissionsForRole(role).contains(permission);
    }

    public static void requirePermission(Context ctx, String permission) {
        String role = ctx.attribute("rol");
        if (!hasPermission(role, permission)) {
            throw new ApiException(403, "Permiso requerido: " + permission);
        }
    }

    public static void requireAnyPermission(Context ctx, String... permissions) {
        String role = ctx.attribute("rol");
        for (String permission : permissions) {
            if (hasPermission(role, permission)) {
                return;
            }
        }
        throw new ApiException(403, "Permiso requerido");
    }

    public static void requireRole(Context ctx, String... roles) {
        String current = ctx.attribute("rol");
        if (current == null) {
            throw new ApiException(403, "Rol requerido");
        }
        for (String role : roles) {
            if (current.equalsIgnoreCase(role)) {
                return;
            }
        }
        throw new ApiException(403, "Rol no autorizado");
    }
}
