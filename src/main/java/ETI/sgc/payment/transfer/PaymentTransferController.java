package ETI.sgc.payment.transfer;

import ETI.sgc.dao.UsuarioDAO;
import ETI.sgc.error.ApiException;
import ETI.sgc.model.Usuario;
import ETI.sgc.security.Rbac;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class PaymentTransferController {
    private final PaymentTransferService service;
    private final UsuarioDAO usuarioDAO;

    public PaymentTransferController(PaymentTransferService service, UsuarioDAO usuarioDAO) {
        this.service = service;
        this.usuarioDAO = usuarioDAO;
    }

    public void routes(Javalin app) {
        app.post("/api/mobile/v1/payments/transfer", ctx -> {
            Usuario user = currentUser(ctx);
            PaymentTransferRequest request = new PaymentTransferRequest();
            request.obligacion_id = Long.parseLong(requiredForm(ctx, "obligacion_id"));
            request.amount = requiredForm(ctx, "amount");
            request.method = valueOrDefault(ctx.formParam("method"), "Transferencia bancaria");
            request.bank = valueOrDefault(ctx.formParam("bank"), "");
            request.reference = requiredForm(ctx, "reference");
            ctx.status(201).json(service.submit(user, request, ctx.uploadedFile("support")));
        });

        app.get("/api/mobile/v1/payments/transfer", ctx ->
                ctx.json(service.listMine(currentUser(ctx)))
        );

        app.get("/api/mobile/v1/payments/transfer/{id}/support", ctx -> {
            Map<String, Object> transfer = service.findForUser(Long.parseLong(ctx.pathParam("id")), currentUser(ctx));
            stream(ctx, service.resolveSupport(transfer), String.valueOf(transfer.get("support_mime_type")), false,
                    String.valueOf(transfer.get("support_original_filename")));
        });

        app.get("/api/mobile/v1/payments/transfer/{id}/receipt", ctx -> {
            Map<String, Object> transfer = service.findForUser(Long.parseLong(ctx.pathParam("id")), currentUser(ctx));
            stream(ctx, service.resolveReceipt(transfer), "application/pdf", true,
                    String.valueOf(transfer.get("receipt_number")) + ".pdf");
        });

        app.post("/api/payments/transfers", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_CREAR);
            PaymentTransferRequest request = new PaymentTransferRequest();
            request.obligacion_id = Long.parseLong(requiredForm(ctx, "obligacion_id"));
            request.amount = requiredForm(ctx, "amount");
            request.method = valueOrDefault(ctx.formParam("method"), "Transferencia bancaria");
            request.bank = valueOrDefault(ctx.formParam("bank"), "");
            request.reference = requiredForm(ctx, "reference");
            ctx.status(201).json(service.submitBackoffice(currentUser(ctx), request, ctx.uploadedFile("support")));
        });

        app.get("/api/payments/transfers", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_VALIDAR);
            int page = queryInt(ctx, "page", 1);
            int size = queryInt(ctx, "size", 25);
            ctx.json(service.listAdmin(ctx.queryParam("status"), page, size));
        });

        app.get("/api/payments/transfers/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_VALIDAR);
            ctx.json(service.findById(Long.parseLong(ctx.pathParam("id"))));
        });

        app.get("/api/payments/transfers/{id}/history", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_VALIDAR);
            ctx.json(service.history(Long.parseLong(ctx.pathParam("id"))));
        });

        app.get("/api/payments/transfers/{id}/support", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_VALIDAR);
            Map<String, Object> transfer = service.findById(Long.parseLong(ctx.pathParam("id")));
            stream(ctx, service.resolveSupport(transfer), String.valueOf(transfer.get("support_mime_type")), false,
                    String.valueOf(transfer.get("support_original_filename")));
        });

        app.get("/api/payments/transfers/{id}/receipt", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_VALIDAR);
            Map<String, Object> transfer = service.findById(Long.parseLong(ctx.pathParam("id")));
            stream(ctx, service.resolveReceipt(transfer), "application/pdf", true,
                    String.valueOf(transfer.get("receipt_number")) + ".pdf");
        });

        app.patch("/api/payments/transfers/{id}/approve", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_VALIDAR);
            PaymentTransferReviewRequest request = ctx.bodyAsClass(PaymentTransferReviewRequest.class);
            ctx.json(service.approve(
                    Long.parseLong(ctx.pathParam("id")),
                    ctx.attribute("username"),
                    request == null ? "" : request.observations
            ));
        });

        app.patch("/api/payments/transfers/{id}/reject", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_VALIDAR);
            PaymentTransferReviewRequest request = ctx.bodyAsClass(PaymentTransferReviewRequest.class);
            ctx.json(service.reject(
                    Long.parseLong(ctx.pathParam("id")),
                    ctx.attribute("username"),
                    request == null ? "" : request.observations
            ));
        });
    }

    private Usuario currentUser(Context ctx) {
        String username = ctx.attribute("username");
        Usuario user = usuarioDAO.buscarPorUsername(username);
        if (user == null || !user.activo) {
            throw new ApiException(401, "Usuario no disponible");
        }
        return user;
    }

    private String requiredForm(Context ctx, String name) {
        String value = ctx.formParam(name);
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "Campo requerido: " + name);
        }
        return value.trim();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private int queryInt(Context ctx, String name, int fallback) {
        try {
            String value = ctx.queryParam(name);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void stream(Context ctx, Path file, String mimeType, boolean attachment, String filename) throws Exception {
        String resolvedMime = mimeType == null || mimeType.isBlank() || "null".equals(mimeType)
                ? Files.probeContentType(file)
                : mimeType;
        ctx.contentType(resolvedMime == null ? "application/octet-stream" : resolvedMime);
        ctx.header("Content-Disposition", (attachment ? "attachment" : "inline") + "; filename=\"" + filename + "\"");
        ctx.result(Files.newInputStream(file));
    }
}
