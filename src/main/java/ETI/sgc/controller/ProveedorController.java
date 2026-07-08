package ETI.sgc.controller;

import ETI.sgc.dao.ProveedorDAO;
import ETI.sgc.dto.ProveedorRequest;
import ETI.sgc.security.Rbac;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;

public class ProveedorController {

    private final ProveedorDAO dao;

    public ProveedorController(ProveedorDAO dao) {
        this.dao = dao;
    }

    public void routes(Javalin app) {

        // 🔹 LISTAR
        app.get("/api/proveedores", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            ctx.json(dao.listar());
        });

        // 🔹 OBTENER
        app.get("/api/proveedores/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_VER);
            Long id = Long.parseLong(ctx.pathParam("id"));
            Object p = dao.obtener(id);
            if (p == null) ctx.status(404).json(Map.of("error", "Proveedor no encontrado"));
            else ctx.json(p);
        });

        // 🔹 CREAR
        app.post("/api/proveedores", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            ProveedorRequest req = ctx.bodyAsClass(ProveedorRequest.class);
            validarYLimpiar(ctx, req); // Si falla, lanza excepción que Javalin maneja

            Long id = dao.crear(req);
            ctx.status(201).json(Map.of("id", id, "message", "Proveedor registrado"));
        });

        // 🔹 ACTUALIZAR
        app.put("/api/proveedores/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            Long id = Long.parseLong(ctx.pathParam("id"));
            ProveedorRequest req = ctx.bodyAsClass(ProveedorRequest.class);
            validarYLimpiar(ctx, req);

            dao.actualizar(id, req);
            ctx.json(Map.of("message", "Proveedor actualizado correctamente"));
        });

        // 🔹 ESTADO
        app.patch("/api/proveedores/{id}/estado", ctx -> {
            Rbac.requirePermission(ctx, Rbac.TERCEROS_GESTIONAR);
            Long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            boolean activo = Boolean.parseBoolean(body.get("activo").toString());

            dao.cambiarEstado(id, activo);
            ctx.json(Map.of("message", "Estado del proveedor actualizado"));
        });
    }

    // 🔥 Método de validación centralizado para no repetir código
    private void validarYLimpiar(Context ctx, ProveedorRequest req) {
        if (req.tipo == null) throw new io.javalin.http.BadRequestResponse("El tipo es obligatorio");

        if (req.tipo.equals("PERSONA")) {
            if (req.nombre == null || req.nombre.isBlank()) throw new io.javalin.http.BadRequestResponse("Nombre obligatorio para persona");
            if (req.nit == null || req.nit.isBlank()) throw new io.javalin.http.BadRequestResponse("Cédula obligatoria");
            req.razon_social = null; // Limpieza por seguridad
        }
        else if (req.tipo.equals("EMPRESA")) {
            if (req.razon_social == null || req.razon_social.isBlank()) throw new io.javalin.http.BadRequestResponse("Razón social obligatoria");
            if (req.nit == null || req.nit.isBlank()) throw new io.javalin.http.BadRequestResponse("NIT obligatorio");
            req.nombre = null; // Limpieza por seguridad
        }
        else {
            throw new io.javalin.http.BadRequestResponse("Tipo inválido (PERSONA o EMPRESA)");
        }
    }
}
