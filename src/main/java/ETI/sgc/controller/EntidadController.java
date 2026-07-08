package ETI.sgc.controller;

import ETI.sgc.dao.EntidadDAO;
import ETI.sgc.dto.EntidadRequest;
import ETI.sgc.security.Rbac;
import io.javalin.Javalin;
import java.util.Map;

public class EntidadController {

    private final EntidadDAO dao;

    public EntidadController(EntidadDAO dao) {
        this.dao = dao;
    }

    public void routes(Javalin app) {

        // 🔹 CREAR
        app.post("/api/entidades", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            EntidadRequest req = ctx.bodyAsClass(EntidadRequest.class);
            if (req.nombre == null || req.nombre.isBlank()) {
                ctx.status(400).json(Map.of("error", "Nombre obligatorio"));
                return;
            }
            Long id = dao.crear(req);
            ctx.status(201).json(Map.of("id", id, "message", "Entidad creada"));
        });

        // 🔹 LISTAR
        app.get("/api/entidades", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            ctx.json(dao.listar());
        });

        // 🔹 OBTENER
        app.get("/api/entidades/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            Long id = Long.parseLong(ctx.pathParam("id"));
            Object entidad = dao.obtener(id);
            if (entidad == null) {
                ctx.status(404).json(Map.of("error", "Entidad no encontrada"));
            } else {
                ctx.json(entidad);
            }
        });

        // 🔹 ACTUALIZAR
        app.put("/api/entidades/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            Long id = Long.parseLong(ctx.pathParam("id"));
            EntidadRequest req = ctx.bodyAsClass(EntidadRequest.class);
            dao.actualizar(id, req);
            ctx.json(Map.of("message", "Entidad actualizada"));
        });

        // 🔹 ESTADO
        app.patch("/api/entidades/{id}/estado", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            Long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            boolean activo = (boolean) body.getOrDefault("activo", false);
            dao.cambiarEstado(id, activo);
            ctx.json(Map.of("message", "Estado actualizado", "activo", activo));
        });
    }
}
