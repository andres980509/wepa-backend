package ETI.sgc.controller;

import ETI.sgc.dao.IntegranteDAO;
import ETI.sgc.dao.UsuarioDAO;
import ETI.sgc.dto.IntegranteRequest;
import ETI.sgc.error.ApiException;
import ETI.sgc.model.Usuario;
import ETI.sgc.security.Rbac;
import io.javalin.Javalin;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class IntegranteController {

    private final IntegranteDAO dao;
    private final UsuarioDAO usuarioDao;

    public IntegranteController(IntegranteDAO dao) {
        this(dao, null);
    }

    public IntegranteController(IntegranteDAO dao, UsuarioDAO usuarioDao) {
        this.dao = dao;
        this.usuarioDao = usuarioDao;
    }

    public void routes(Javalin app) {

        // 🔹 CREAR
        app.post("/api/admin/integrantes", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            IntegranteRequest req = ctx.bodyAsClass(IntegranteRequest.class);

            if (req.nombre_completo == null || req.nombre_completo.isBlank()) {
                ctx.status(400).json(Map.of("error", "El nombre es obligatorio"));
                return;
            }

            Long id = dao.crear(req);
            ctx.status(201).json(Map.of("id", id, "message", "Integrante creado exitosamente"));
        });

        // 🔹 LISTAR
        app.get("/api/admin/integrantes", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            ctx.json(dao.listar());
        });

        // 🔹 LISTAR
        app.get("/api/admin/integrantes2", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            ctx.json(dao.listar2());
        });



        // 🔹 POR ID
        app.get("/api/admin/integrantes/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            Long id = Long.parseLong(ctx.pathParam("id"));
            Object integrante = dao.obtener(id);
            if (integrante == null) {
                ctx.status(404).json(Map.of("error", "Integrante no encontrado"));
            } else {
                ctx.json(integrante);
            }
        });

        app.get("/api/admin/integrantes/{id}/usuario", ctx -> {
            Rbac.requirePermission(ctx, Rbac.USUARIOS_GESTIONAR);
            Long id = Long.parseLong(ctx.pathParam("id"));
            requireIntegrante(id);
            Usuario user = usuarioDao.buscarPorIntegranteId(id);
            if (user == null) {
                ctx.status(404).json(Map.of("error", "El integrante no tiene usuario movil asociado"));
                return;
            }
            ctx.json(publicUser(user));
        });

        app.post("/api/admin/integrantes/{id}/usuario", ctx -> {
            Rbac.requirePermission(ctx, Rbac.USUARIOS_GESTIONAR);
            Long integranteId = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> integrante = requireIntegrante(integranteId);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);

            Usuario linked = usuarioDao.buscarPorIntegranteId(integranteId);
            if (linked != null) {
                ctx.status(409).json(Map.of("error", "El integrante ya tiene usuario asociado", "usuario", publicUser(linked)));
                return;
            }

            String username = stringValue(body.get("username"));
            String password = stringValue(body.get("password"));
            if (isBlank(username)) {
                username = defaultUsername(integrante);
            }
            if (isBlank(password)) {
                password = defaultPassword(integrante);
            }
            if (isBlank(username) || isBlank(password)) {
                ctx.status(400).json(Map.of("error", "username y password son obligatorios"));
                return;
            }
            if (usuarioDao.buscarPorUsername(username) != null) {
                ctx.status(409).json(Map.of("error", "El username ya existe"));
                return;
            }

            Long userId = usuarioDao.crearUsuarioCompleto(
                    username.trim(),
                    BCrypt.hashpw(password, BCrypt.gensalt(12)),
                    "INTEGRANTE",
                    firstNonBlank(stringValue(body.get("cedula")), stringValue(integrante.get("numero_documento")), "INT-" + integranteId),
                    firstNonBlank(stringValue(body.get("nombres")), firstName(stringValue(integrante.get("nombre_completo"))), "Integrante"),
                    firstNonBlank(stringValue(body.get("apellidos")), lastName(stringValue(integrante.get("nombre_completo"))), "WEPA"),
                    firstNonBlank(stringValue(body.get("email")), stringValue(integrante.get("correo")), username + "@wepa.local"),
                    firstNonBlank(stringValue(body.get("telefono")), stringValue(integrante.get("telefono")), "0000000000"),
                    firstNonBlank(stringValue(body.get("direccion")), stringValue(integrante.get("direccion")), "WEPA"),
                    integranteId
            );

            Usuario created = usuarioDao.buscarPorId(userId);
            ctx.status(201).json(Map.of(
                    "message", "Usuario movil de integrante creado",
                    "usuario", publicUser(created),
                    "credenciales_prueba", Map.of("username", username, "password", password)
            ));
        });

        app.patch("/api/admin/integrantes/{id}/usuario/estado", ctx -> {
            Rbac.requirePermission(ctx, Rbac.USUARIOS_GESTIONAR);
            Long integranteId = Long.parseLong(ctx.pathParam("id"));
            requireIntegrante(integranteId);
            Usuario user = requireUsuarioIntegrante(integranteId);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            if (!body.containsKey("activo")) {
                ctx.status(400).json(Map.of("error", "Falta el campo activo"));
                return;
            }
            boolean activo = Boolean.parseBoolean(String.valueOf(body.get("activo")));
            usuarioDao.cambiarEstado(user.id, activo);
            ctx.json(Map.of("message", "Estado de usuario movil actualizado", "usuario_id", user.id, "activo", activo));
        });

        app.patch("/api/admin/integrantes/{id}/usuario/password", ctx -> {
            Rbac.requirePermission(ctx, Rbac.USUARIOS_GESTIONAR);
            Long integranteId = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> integrante = requireIntegrante(integranteId);
            Usuario user = requireUsuarioIntegrante(integranteId);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String password = stringValue(body.get("password"));
            if (isBlank(password)) {
                password = defaultPassword(integrante);
            }
            usuarioDao.resetPassword(user.id, BCrypt.hashpw(password, BCrypt.gensalt(12)));
            ctx.json(Map.of(
                    "message", "Password de usuario movil actualizado",
                    "usuario_id", user.id,
                    "credenciales_prueba", Map.of("username", user.username, "password", password)
            ));
        });

        app.delete("/api/admin/integrantes/{id}/usuario", ctx -> {
            Rbac.requirePermission(ctx, Rbac.USUARIOS_GESTIONAR);
            Long integranteId = Long.parseLong(ctx.pathParam("id"));
            requireIntegrante(integranteId);
            usuarioDao.desasociarIntegrante(integranteId);
            ctx.json(Map.of("message", "Usuario movil desasociado del integrante"));
        });

        // 🔹 POR CODIGO (QR/NFC)
        app.get("/api/admin/integrantes/codigo/{codigo}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            ctx.json(dao.obtenerPorCodigo(ctx.pathParam("codigo")));
        });

        // 🔹 ACTUALIZAR
        app.put("/api/admin/integrantes/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            Long id = Long.parseLong(ctx.pathParam("id"));
            IntegranteRequest req = ctx.bodyAsClass(IntegranteRequest.class);
            dao.actualizar(id, req);
            ctx.json(Map.of("message", "Ficha actualizada correctamente"));
        });

        // 🔹 CAMBIAR ESTADO (ACTIVO/INACTIVO)
        app.patch("/api/admin/integrantes/{id}/estado", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            Long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);

            if (!body.containsKey("activo")) {
                ctx.status(400).json(Map.of("error", "Falta el campo 'activo'"));
                return;
            }

            boolean activo = Boolean.parseBoolean(body.get("activo").toString());
            dao.cambiarEstado(id, activo);
            ctx.json(Map.of("message", "Estado actualizado"));
        });
// 🔹 SUBIR O CAMBIAR FOTO (Endpoint Independiente)
        app.get("/api/admin/integrantes/{id}/foto", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            Long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> integrante = requireIntegrante(id);
            String fotoUrl = stringValue(integrante.get("foto_url"));
            if (isBlank(fotoUrl)) {
                throw new ApiException(404, "El integrante no tiene foto");
            }

            Path uploads = Path.of("uploads").toAbsolutePath().normalize();
            Path file = uploads.resolve(fotoUrl).normalize();
            if (!file.startsWith(uploads) || !Files.exists(file)) {
                throw new ApiException(404, "Foto no encontrada");
            }

            String contentType = Files.probeContentType(file);
            ctx.contentType(contentType == null ? "application/octet-stream" : contentType);
            ctx.header("Cache-Control", "private, max-age=300");
            ctx.result(Files.newInputStream(file));
        });

        app.patch("/api/admin/integrantes/{id}/foto", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            Long id = Long.parseLong(ctx.pathParam("id"));
            var uploadedFile = ctx.uploadedFile("foto");

            if (uploadedFile == null) {
                ctx.status(400).json(Map.of("error", "No se seleccionó ninguna imagen"));
                return;
            }

            // 1. LIMPIEZA: Obtener foto antigua y borrarla del disco
            // Usamos el método obtener(id) de tu DAO que retorna un Map
            Map<String, Object> integranteActual = (Map<String, Object>) dao.obtener(id);

            if (integranteActual != null && integranteActual.get("foto_url") != null) {
                String fotoViejaRelativa = (String) integranteActual.get("foto_url");
                // La ruta física es uploads + la ruta guardada en BD
                java.io.File archivoParaBorrar = new java.io.File("uploads/" + fotoViejaRelativa);

                if (archivoParaBorrar.exists()) {
                    archivoParaBorrar.delete();
                    System.out.println("Archivo antiguo eliminado: " + fotoViejaRelativa);
                }
            }

            // 2. PROCESAR NUEVA FOTO
            String carpetaRelativa = "perfiles/integrantes";
            java.io.File uploadDir = new java.io.File("uploads/" + carpetaRelativa);
            if (!uploadDir.exists()) uploadDir.mkdirs();

            String extension = uploadedFile.extension();
            String nombreArchivo = "perfil_" + id + "_" + System.currentTimeMillis() + extension;

            String rutaDisco = "uploads/" + carpetaRelativa + "/" + nombreArchivo;
            String rutaBD = carpetaRelativa + "/" + nombreArchivo;

            // Guardar en disco
            io.javalin.util.FileUtil.streamToFile(uploadedFile.content(), rutaDisco);

            // Actualizar BD
          dao.actualizarFoto(id, rutaBD);

            ctx.json(Map.of(
                    "message", "Foto actualizada con éxito",
                    "foto_url", rutaBD
            ));
        });
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ApiException(403, "Solo ADMIN puede gestionar usuarios de integrantes");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireIntegrante(Long id) {
        Map<String, Object> integrante = (Map<String, Object>) dao.obtener(id);
        if (integrante == null) {
            throw new ApiException(404, "Integrante no encontrado");
        }
        return integrante;
    }

    private Usuario requireUsuarioIntegrante(Long integranteId) {
        Usuario user = usuarioDao.buscarPorIntegranteId(integranteId);
        if (user == null) {
            throw new ApiException(404, "El integrante no tiene usuario movil asociado");
        }
        return user;
    }

    private Map<String, Object> publicUser(Usuario user) {
        return Map.of(
                "id", user.id,
                "username", user.username,
                "rol", user.rol,
                "activo", user.activo,
                "integrante_id", user.integrante_id == null ? "" : user.integrante_id
        );
    }

    private String defaultUsername(Map<String, Object> integrante) {
        String document = stringValue(integrante.get("numero_documento"));
        if (!isBlank(document)) {
            return "int" + document.replaceAll("[^A-Za-z0-9]", "");
        }
        return "integrante" + integrante.get("id");
    }

    private String defaultPassword(Map<String, Object> integrante) {
        String document = stringValue(integrante.get("numero_documento"));
        if (!isBlank(document) && document.length() >= 4) {
            return document;
        }
        return "wepa" + integrante.get("id");
    }

    private String firstName(String fullName) {
        if (isBlank(fullName)) return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 0 ? "" : parts[0];
    }

    private String lastName(String fullName) {
        if (isBlank(fullName)) return "";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length <= 1) return "WEPA";
        return fullName.trim().substring(parts[0].length()).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
