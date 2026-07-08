package ETI.sgc.controller;

import ETI.sgc.dao.PatrocinadorDAO;
import ETI.sgc.dto.PatrocinadorRequest;
import ETI.sgc.security.Rbac;
import io.javalin.Javalin;
import java.util.Map;

public class PatrocinadorController {

    private final PatrocinadorDAO dao;

    public PatrocinadorController(PatrocinadorDAO dao) {
        this.dao = dao;
    }

    public void routes(Javalin app) {

        // 🔹 CREAR
        app.post("/api/patrocinadores", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            PatrocinadorRequest req = ctx.bodyAsClass(PatrocinadorRequest.class);

            if (req.nombre == null || req.nombre.isBlank()) {
                ctx.status(400).json(Map.of("error", "El nombre es obligatorio"));
                return;
            }

            Long id = dao.crear(req);
            ctx.status(201).json(Map.of("id", id, "message", "Patrocinador creado"));
        });

        // 🔹 LISTAR
        app.get("/api/patrocinadores", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            // Ya no devuelve una lista dentro de otra lista
            ctx.json(dao.listar());
        });

        // 🔹 OBTENER
        app.get("/api/patrocinadores/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            Long id = Long.parseLong(ctx.pathParam("id"));
            Object p = dao.obtener(id);
            if (p == null) {
                ctx.status(404).json(Map.of("error", "Patrocinador no encontrado"));
            } else {
                ctx.json(p);
            }
        });

        // 🔹 ACTUALIZAR
        app.put("/api/patrocinadores/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            Long id = Long.parseLong(ctx.pathParam("id"));
            PatrocinadorRequest req = ctx.bodyAsClass(PatrocinadorRequest.class);

            dao.actualizar(id, req);
            ctx.json(Map.of("message", "Actualizado correctamente"));
        });

        // 🔹 ESTADO
        app.patch("/api/patrocinadores/{id}/estado", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            Long id = Long.parseLong(ctx.pathParam("id"));

            // Recibimos el body como mapa y extraemos el booleano de forma segura
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            boolean activo = (boolean) body.getOrDefault("activo", false);

            dao.cambiarEstado(id, activo);
            ctx.json(Map.of("message", "Estado actualizado", "activo", activo));
        });
    }
}
