package ETI.sgc.payment;

import ETI.sgc.error.ApiException;
import ETI.sgc.security.Rbac;
import io.javalin.Javalin;

import java.util.Map;

public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void routes(Javalin app) {
        app.post("/api/payments/initiate", ctx -> {
            PaymentInitRequest request = ctx.bodyAsClass(PaymentInitRequest.class);
            if (isIntegrante(ctx)) {
                ctx.status(201).json(paymentService.initiateForIntegrante(
                        request,
                        requiredIntegranteId(ctx),
                        ctx.attribute("username"),
                        ctx.ip()
                ));
                return;
            }

            Rbac.requirePermission(ctx, Rbac.PAGOS_CREAR);
            ctx.status(201).json(paymentService.initiate(
                    request,
                    ctx.attribute("username"),
                    ctx.ip()
            ));
        });

        app.get("/api/payments/obligacion/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_CREAR);
            ctx.json(paymentService.listByObligacion(Long.parseLong(ctx.pathParam("id"))));
        });

        app.post("/api/payments/cleanup-pending", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_CREAR);
            PaymentCleanupRequest request = ctx.bodyAsClass(PaymentCleanupRequest.class);
            ctx.json(paymentService.cleanupPending(request, ctx.attribute("username"), ctx.ip()));
        });

        app.post("/api/payments/{id}/reverse", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_REVERSAR);
            Map<String, Object> payload = ctx.bodyAsClass(Map.class);
            String reason = payload.get("reason") == null ? "" : String.valueOf(payload.get("reason"));
            ctx.json(paymentService.reverse(Long.parseLong(ctx.pathParam("id")), reason, ctx.attribute("username"), ctx.ip()));
        });

        app.post("/api/payments/webhooks/mercadopago", ctx ->
                ctx.json(paymentService.processWebhook(PaymentProviderType.MERCADOPAGO, ctx, ctx.body()))
        );

        app.post("/api/payments/webhooks/{provider}", ctx -> {
            PaymentProviderType provider = parseProvider(ctx.pathParam("provider"));
            ctx.json(paymentService.processWebhook(provider, ctx, ctx.body()));
        });
    }

    private PaymentProviderType parseProvider(String provider) {
        try {
            return PaymentProviderType.valueOf(provider.trim().toUpperCase());
        } catch (Exception e) {
            throw new ApiException(400, "Proveedor invalido");
        }
    }

    private boolean isIntegrante(io.javalin.http.Context ctx) {
        String role = ctx.attribute("rol");
        return "INTEGRANTE".equalsIgnoreCase(role);
    }

    private Long requiredIntegranteId(io.javalin.http.Context ctx) {
        Object value = ctx.attribute("integrante_id");
        Long id = toLong(value);
        if (id == null) {
            throw new ApiException(403, "Usuario movil sin integrante asociado");
        }
        return id;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
