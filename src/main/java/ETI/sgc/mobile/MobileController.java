package ETI.sgc.mobile;

import ETI.sgc.dao.IntegranteDAO;
import ETI.sgc.dao.MovimientoDAO;
import ETI.sgc.dao.ObligacionDAO;
import ETI.sgc.dao.UsuarioDAO;
import ETI.sgc.document.DocumentService;
import ETI.sgc.error.ApiException;
import ETI.sgc.model.Usuario;
import ETI.sgc.security.JwtUtil;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class MobileController {
    private final UsuarioDAO usuarioDAO;
    private final IntegranteDAO integranteDAO;
    private final ObligacionDAO obligacionDAO;
    private final MovimientoDAO movimientoDAO;
    private final DocumentService documentService;
    private final RefreshTokenService refreshTokenService;
    private final PushTokenDAO pushTokenDAO;

    public MobileController(
            UsuarioDAO usuarioDAO,
            IntegranteDAO integranteDAO,
            ObligacionDAO obligacionDAO,
            MovimientoDAO movimientoDAO,
            DocumentService documentService,
            RefreshTokenService refreshTokenService,
            PushTokenDAO pushTokenDAO
    ) {
        this.usuarioDAO = usuarioDAO;
        this.integranteDAO = integranteDAO;
        this.obligacionDAO = obligacionDAO;
        this.movimientoDAO = movimientoDAO;
        this.documentService = documentService;
        this.refreshTokenService = refreshTokenService;
        this.pushTokenDAO = pushTokenDAO;
    }

    public void routes(Javalin app) {
        app.post("/api/mobile/v1/login", ctx -> {
            MobileLoginRequest req = ctx.bodyAsClass(MobileLoginRequest.class);
            Usuario user = authenticate(req);
            requireMobileIntegrante(user);
            String accessToken = JwtUtil.generarMobile(user);
            String refreshToken = refreshTokenService.create(user.id, req.device_name);
            ctx.json(Map.of(
                    "access_token", accessToken,
                    "refresh_token", refreshToken,
                    "token_type", "Bearer",
                    "user", userPayload(user)
            ));
        });

        app.post("/api/mobile/v1/refresh", ctx -> {
            MobileRefreshRequest req = ctx.bodyAsClass(MobileRefreshRequest.class);
            Map<String, Object> row = refreshTokenService.validate(req.refresh_token);
            Long usuarioId = ((Number) row.get("usuario_id")).longValue();
            Usuario user = usuarioDAO.buscarPorId(usuarioId);
            if (user == null || !user.activo) {
                throw new ApiException(401, "Usuario no disponible");
            }
            requireMobileIntegrante(user);
            ctx.json(Map.of("access_token", JwtUtil.generarMobile(user), "token_type", "Bearer"));
        });

        app.post("/api/mobile/v1/logout", ctx -> {
            MobileRefreshRequest req = ctx.bodyAsClass(MobileRefreshRequest.class);
            refreshTokenService.revoke(req.refresh_token);
            ctx.json(Map.of("message", "Sesion cerrada"));
        });

        app.get("/api/mobile/v1/perfil", ctx -> {
            String username = ctx.attribute("username");
            Usuario user = usuarioDAO.buscarPorUsername(username);
            requireMobileIntegrante(user);
            ctx.json(userPayload(user));
        });

        app.get("/api/mobile/v1/pagos", ctx -> {
            Long integranteId = resolveIntegranteId(ctx, ctx.queryParam("integrante_id"));
            if (integranteId == null) {
                ctx.json(List.of());
                return;
            }
            ctx.json(obligacionDAO.listarPorIntegrante(integranteId));
        });

        app.get("/api/mobile/v1/pagos/pendientes", ctx -> {
            Long integranteId = resolveIntegranteId(ctx, ctx.queryParam("integrante_id"));
            if (integranteId == null) {
                ctx.json(List.of());
                return;
            }
            ctx.json(obligacionDAO.listarPorTercero(integranteId, "INTEGRANTE"));
        });

        app.get("/api/mobile/v1/pagos/movimientos", ctx ->
                ctx.json(movimientoDAO.listarPorTercero(resolveIntegranteId(ctx, null), "INTEGRANTE"))
        );

        app.get("/api/mobile/v1/pagos/{integranteId}/movimientos", ctx ->
                ctx.json(movimientoDAO.listarPorTercero(resolveIntegranteId(ctx, ctx.pathParam("integranteId")), "INTEGRANTE"))
        );

        app.get("/api/mobile/v1/qr-nfc/{codigo}", ctx -> {
            Object integrante = integranteDAO.obtenerPorCodigo(ctx.pathParam("codigo"));
            if (integrante == null) {
                throw new ApiException(404, "Codigo no encontrado");
            }
            ctx.json(integrante);
        });

        app.get("/api/mobile/v1/asistencia", ctx ->
                ctx.json(Map.of("data", List.of(), "message", "Modulo de asistencia listo para implementar eventos"))
        );

        app.get("/api/mobile/v1/documentos", ctx -> {
            Usuario user = currentMobileUser(ctx);
            ctx.json(documentService.listByEntity("INTEGRANTE", user.integrante_id));
        });

        app.post("/api/mobile/v1/documentos", ctx -> {
            Usuario user = currentMobileUser(ctx);
            ctx.status(201).json(documentService.upload(
                    ctx.uploadedFile("file"),
                    "INTEGRANTE",
                    user.integrante_id,
                    ctx.formParam("tipo_documento") == null ? "SOPORTE" : ctx.formParam("tipo_documento"),
                    ctx.formParam("folder_path") == null ? "mobile" : ctx.formParam("folder_path"),
                    ctx.formParam("fecha_vencimiento"),
                    ctx.formParam("observaciones"),
                    user.username,
                    ctx.ip()
            ));
        });

        app.get("/api/mobile/v1/documentos/{id}/preview", ctx -> {
            Usuario user = currentMobileUser(ctx);
            Map<String, Object> document = mobileDocument(ctx.pathParam("id"), user);
            documentService.recordAccess(((Number) document.get("id")).longValue(), "PREVIEW_MOBILE", user.username, ctx.ip());
            streamDocument(ctx, document, false);
        });

        app.get("/api/mobile/v1/documentos/{id}/download", ctx -> {
            Usuario user = currentMobileUser(ctx);
            Map<String, Object> document = mobileDocument(ctx.pathParam("id"), user);
            documentService.recordAccess(((Number) document.get("id")).longValue(), "DESCARGA_MOBILE", user.username, ctx.ip());
            streamDocument(ctx, document, true);
        });

        app.get("/api/mobile/v1/documentos/{entidadTipo}/{entidadId}", ctx -> {
            String entidadTipo = ctx.pathParam("entidadTipo");
            Long entidadId = Long.parseLong(ctx.pathParam("entidadId"));
            if ("INTEGRANTE".equalsIgnoreCase(entidadTipo)) {
                entidadId = resolveIntegranteId(ctx, String.valueOf(entidadId));
            } else if (isIntegranteRole(ctx)) {
                throw new ApiException(403, "Un integrante solo puede consultar sus documentos");
            }
            ctx.json(documentService.listByEntity(entidadTipo, entidadId));
        });

        app.get("/api/mobile/v1/notificaciones", ctx ->
                ctx.json(Map.of("data", List.of(), "message", "Push FCM/APNs activo para dispositivos registrados"))
        );

        app.post("/api/mobile/v1/push-token", ctx -> {
            Usuario user = currentMobileUser(ctx);
            MobilePushTokenRequest req = ctx.bodyAsClass(MobilePushTokenRequest.class);
            if (req == null || req.token == null || req.token.isBlank()) {
                throw new ApiException(400, "Token push requerido");
            }
            pushTokenDAO.upsert(
                    user.id,
                    user.integrante_id,
                    req.token.trim(),
                    normalizeCode(req.platform, "ANDROID"),
                    normalizeCode(req.provider, "FCM"),
                    emptyToNull(req.device_name),
                    emptyToNull(req.app_version)
            );
            ctx.json(Map.of("message", "Dispositivo registrado para push", "provider", normalizeCode(req.provider, "FCM")));
        });

        app.delete("/api/mobile/v1/push-token", ctx -> {
            Usuario user = currentMobileUser(ctx);
            MobilePushTokenRequest req = ctx.bodyAsClass(MobilePushTokenRequest.class);
            if (req == null || req.token == null || req.token.isBlank()) {
                throw new ApiException(400, "Token push requerido");
            }
            pushTokenDAO.deactivate(user.id, req.token.trim());
            ctx.json(Map.of("message", "Dispositivo desactivado para push"));
        });
    }

    private Usuario authenticate(MobileLoginRequest req) {
        if (req == null || req.username == null || req.password == null) {
            throw new ApiException(400, "Credenciales requeridas");
        }

        Usuario user = usuarioDAO.buscarPorUsername(req.username.trim());
        if (user == null || !BCrypt.checkpw(req.password, user.password_hash)) {
            throw new ApiException(401, "Credenciales invalidas");
        }
        if (!user.activo) {
            throw new ApiException(403, "Usuario inactivo");
        }
        return user;
    }

    private Long queryLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value);
    }

    private Map<String, Object> userPayload(Usuario user) {
        return Map.of(
                "id", user.id,
                "username", user.username,
                "rol", user.rol,
                "activo", user.activo,
                "integrante_id", user.integrante_id == null ? "" : user.integrante_id
        );
    }

    private Usuario currentMobileUser(Context ctx) {
        String username = ctx.attribute("username");
        Usuario user = usuarioDAO.buscarPorUsername(username);
        requireMobileIntegrante(user);
        return user;
    }

    private void requireMobileIntegrante(Usuario user) {
        if (user == null || !user.activo) {
            throw new ApiException(401, "Usuario no disponible");
        }
        if (!"INTEGRANTE".equalsIgnoreCase(user.rol)) {
            throw new ApiException(403, "La app movil WEPA es solo para integrantes");
        }
        if (user.integrante_id == null) {
            throw new ApiException(403, "Usuario movil sin integrante asociado");
        }
    }

    private Long resolveIntegranteId(Context ctx, String requestedValue) {
        String username = ctx.attribute("username");
        Usuario user = usuarioDAO.buscarPorUsername(username);
        requireMobileIntegrante(user);

        if ("INTEGRANTE".equalsIgnoreCase(user.rol)) {
            if (user.integrante_id == null) {
                throw new ApiException(403, "Usuario movil sin integrante asociado");
            }
            Long requested = queryLong(requestedValue);
            if (requested != null && !requested.equals(user.integrante_id)) {
                throw new ApiException(403, "No puede consultar informacion de otro integrante");
            }
            return user.integrante_id;
        }

        return queryLong(requestedValue);
    }

    private boolean isIntegranteRole(Context ctx) {
        String role = ctx.attribute("rol");
        return "INTEGRANTE".equalsIgnoreCase(role);
    }

    private Map<String, Object> mobileDocument(String idValue, Usuario user) {
        Long id = Long.parseLong(idValue);
        Map<String, Object> document = documentService.findById(id);
        String entidadTipo = String.valueOf(document.get("entidad_tipo"));
        Long entidadId = ((Number) document.get("entidad_id")).longValue();
        if (!"INTEGRANTE".equalsIgnoreCase(entidadTipo) || !entidadId.equals(user.integrante_id)) {
            throw new ApiException(403, "No puede consultar documentos de otro integrante");
        }
        return document;
    }

    private void streamDocument(Context ctx, Map<String, Object> document, boolean attachment) throws Exception {
        Path file = documentService.resolveFile(document);
        String filename = String.valueOf(document.get("original_filename"));
        String mimeType = String.valueOf(document.get("mime_type"));
        String resolvedMime = mimeType == null || "null".equals(mimeType) ? Files.probeContentType(file) : mimeType;
        ctx.contentType(resolvedMime == null ? "application/octet-stream" : resolvedMime);
        ctx.header("Content-Disposition", (attachment ? "attachment" : "inline") + "; filename=\"" + filename + "\"");
        ctx.result(Files.newInputStream(file));
    }

    private String normalizeCode(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase().replaceAll("[^A-Z0-9_]", "");
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
