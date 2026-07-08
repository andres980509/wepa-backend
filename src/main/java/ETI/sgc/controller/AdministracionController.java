// ETI.sgc.controller.AdministracionController.java
package ETI.sgc.controller;

import ETI.sgc.dao.ConceptoDAO;
import ETI.sgc.dto.ConceptoRequest;
import ETI.sgc.security.Rbac;
import io.javalin.Javalin;
import java.util.Map;

public class AdministracionController {
    private final ConceptoDAO conceptoDAO;

    public AdministracionController(ConceptoDAO cDAO) {
        this.conceptoDAO = cDAO;
    }

    public void routes(Javalin app) {
        // Crear Concepto + Validaciones (Todo en uno)
        app.post("/api/admin/conceptos", ctx -> {
            Rbac.requirePermission(ctx, Rbac.CONFIGURACION_GESTIONAR);
            ConceptoRequest req = ctx.bodyAsClass(ConceptoRequest.class);
            Long id = conceptoDAO.crearCompleto(req);
            ctx.status(201).json(Map.of("id", id, "mensaje", "Configuración guardada"));
        });

        // Listar con sus reglas
        app.get("/api/admin/conceptos", ctx -> {
            Rbac.requireAnyPermission(ctx, Rbac.CONFIGURACION_GESTIONAR, Rbac.FINANZAS_VER);
            ctx.json(conceptoDAO.listarConValidaciones());
        });
    }
}
