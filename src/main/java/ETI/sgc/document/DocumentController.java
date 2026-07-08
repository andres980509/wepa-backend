package ETI.sgc.document;

import ETI.sgc.http.Pagination;
import ETI.sgc.security.Rbac;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    public void routes(Javalin app) {
        app.post("/api/documentos", ctx -> {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_SUBIR);
            DocumentUploadResult result = documentService.upload(
                    ctx.uploadedFile("file"),
                    ctx.formParam("entidad_tipo"),
                    Long.parseLong(ctx.formParam("entidad_id")),
                    ctx.formParam("tipo_documento") == null ? "OTRO" : ctx.formParam("tipo_documento"),
                    ctx.formParam("folder_path"),
                    ctx.formParam("fecha_vencimiento"),
                    ctx.formParam("observaciones"),
                    ctx.attribute("username"),
                    ctx.ip()
            );
            ctx.status(201).json(result);
        });

        app.get("/api/documentos/search", ctx -> {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VER);
            ctx.json(documentService.search(filtersFrom(ctx), Pagination.from(ctx)));
        });

        app.get("/api/documentos/vencidos", ctx -> {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VER);
            ctx.json(documentService.expired(Pagination.from(ctx)));
        });

        app.get("/api/documentos/faltantes", ctx -> {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VER);
            Long entidadId = ctx.queryParam("entidad_id") == null ? null : Long.parseLong(ctx.queryParam("entidad_id"));
            ctx.json(documentService.missing(ctx.queryParam("entidad_tipo"), entidadId));
        });

        app.get("/api/documentos/tipos", ctx -> {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VER);
            ctx.json(documentService.listTypes(ctx.queryParam("entidad_tipo")));
        });

        app.post("/api/documentos/tipos", ctx -> {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VALIDAR);
            ctx.status(201).json(documentService.createType(ctx.bodyAsClass(Map.class), ctx.attribute("username"), ctx.ip()));
        });

        app.get("/api/documentos/dashboard", ctx -> {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VER);
            ctx.json(documentService.dashboard());
        });

        app.get("/api/documentos/entidad/{tipo}/{id}", ctx ->
        {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VER);
            ctx.json(documentService.listByEntity(ctx.pathParam("tipo"), Long.parseLong(ctx.pathParam("id"))));
        }
        );

        app.get("/api/documentos/{id}", ctx ->
        {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VER);
            ctx.json(documentService.findById(Long.parseLong(ctx.pathParam("id"))));
        }
        );

        app.patch("/api/documentos/{id}/estado", ctx -> {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VALIDAR);
            Map<String, Object> payload = ctx.bodyAsClass(Map.class);
            documentService.updateStatus(
                    Long.parseLong(ctx.pathParam("id")),
                    String.valueOf(payload.get("estado")),
                    payload.get("observaciones") == null ? null : String.valueOf(payload.get("observaciones")),
                    ctx.attribute("username"),
                    ctx.ip()
            );
            ctx.json(Map.of("mensaje", "Estado documental actualizado"));
        });

        app.patch("/api/documentos/{id}/validar", ctx -> {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VALIDAR);
            Map<String, Object> payload = ctx.bodyAsClass(Map.class);
            documentService.validateDocument(
                    Long.parseLong(ctx.pathParam("id")),
                    payload.get("observaciones") == null ? null : String.valueOf(payload.get("observaciones")),
                    ctx.attribute("username"),
                    ctx.ip()
            );
            ctx.json(Map.of("mensaje", "Documento validado"));
        });

        app.get("/api/documentos/{id}/versiones", ctx -> {
            Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VER);
            ctx.json(documentService.versions(Long.parseLong(ctx.pathParam("id"))));
        });

        app.get("/api/documentos/{id}/download", ctx -> streamFile(ctx, true));
        app.get("/api/documentos/{id}/preview", ctx -> streamFile(ctx, false));
    }

    private DocumentSearchFilters filtersFrom(Context ctx) {
        DocumentSearchFilters filters = new DocumentSearchFilters();
        filters.entidadTipo = ctx.queryParam("entidad_tipo");
        filters.entidadId = ctx.queryParam("entidad_id") == null ? null : Long.parseLong(ctx.queryParam("entidad_id"));
        filters.tipoDocumento = ctx.queryParam("tipo_documento");
        filters.estado = ctx.queryParam("estado");
        filters.fechaDesde = ctx.queryParam("fecha_desde");
        filters.fechaHasta = ctx.queryParam("fecha_hasta");
        filters.vencimiento = ctx.queryParam("vencimiento");
        filters.nombre = ctx.queryParam("nombre");
        filters.folderPath = ctx.queryParam("folder_path");
        return filters;
    }

    private void streamFile(Context ctx, boolean attachment) throws Exception {
        Rbac.requirePermission(ctx, Rbac.DOCUMENTOS_VER);
        Long id = Long.parseLong(ctx.pathParam("id"));
        Map<String, Object> document = documentService.findById(id);
        Path file = documentService.resolveFile(document);
        String filename = String.valueOf(document.get("original_filename"));
        String mimeType = String.valueOf(document.get("mime_type"));
        documentService.recordAccess(id, attachment ? "DESCARGA" : "PREVIEW", ctx.attribute("username"), ctx.ip());

        ctx.contentType(mimeType == null || "null".equals(mimeType) ? Files.probeContentType(file) : mimeType);
        ctx.header("Content-Disposition", (attachment ? "attachment" : "inline") + "; filename=\"" + filename + "\"");
        ctx.result(Files.newInputStream(file));
    }
}
