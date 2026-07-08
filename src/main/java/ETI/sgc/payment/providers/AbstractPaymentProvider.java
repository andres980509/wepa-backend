package ETI.sgc.payment.providers;

import ETI.sgc.config.AppConfig;
import ETI.sgc.error.ApiException;
import ETI.sgc.payment.PaymentInitRequest;
import ETI.sgc.payment.PaymentProviderResponse;
import ETI.sgc.payment.PaymentStatus;
import ETI.sgc.payment.WebhookResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

import java.util.UUID;

abstract class AbstractPaymentProvider implements PaymentProvider {
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected final AppConfig config;

    protected AbstractPaymentProvider(AppConfig config) {
        this.config = config;
    }

    @Override
    public PaymentProviderResponse createPayment(Long localTransactionId, PaymentInitRequest request) {
        String reference = type().name().toLowerCase() + "-" + localTransactionId + "-" + UUID.randomUUID();
        String checkoutUrl = configuredCheckoutUrl(reference);
        String message = checkoutUrl == null
                ? "Proveedor " + type().name() + " preparado, faltan credenciales/URL de checkout"
                : "Transaccion creada en proveedor " + type().name();
        return new PaymentProviderResponse(reference, checkoutUrl, PaymentStatus.PENDIENTE, message);
    }

    @Override
    public WebhookResult parseAndValidateWebhook(Context ctx, String rawBody) {
        validateSharedSecret(ctx);
        try {
            JsonNode json = MAPPER.readTree(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
            String reference = findText(json, "reference", "provider_reference", "transaction_id", "id");
            String status = findText(json, "status", "estado", "transaction_status");

            if (reference == null || reference.isBlank()) {
                throw new ApiException(400, "Webhook sin referencia de transaccion");
            }

            return new WebhookResult(reference, mapStatus(status), status);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(400, "Webhook invalido");
        }
    }

    protected PaymentStatus mapStatus(String rawStatus) {
        if (rawStatus == null) {
            return PaymentStatus.PENDIENTE;
        }

        String normalized = rawStatus.trim().toUpperCase();
        return switch (normalized) {
            case "APPROVED", "APROBADO", "ACCEPTED", "OK", "PAID", "SUCCESS" -> PaymentStatus.APROBADO;
            case "DECLINED", "REJECTED", "RECHAZADO", "FAILED", "ERROR" -> PaymentStatus.RECHAZADO;
            case "EXPIRED", "EXPIRADO", "CANCELLED", "CANCELED" -> PaymentStatus.EXPIRADO;
            default -> PaymentStatus.PENDIENTE;
        };
    }

    protected String findText(JsonNode json, String... names) {
        for (String name : names) {
            JsonNode current = json.findValue(name);
            if (current != null && !current.isNull()) {
                return current.asText();
            }
        }
        return null;
    }

    private void validateSharedSecret(Context ctx) {
        String expected = config.get(type().name() + "_WEBHOOK_SECRET", "");
        if (expected.isBlank()) {
            return;
        }

        String received = ctx.header("X-WEPA-Webhook-Secret");
        if (!expected.equals(received)) {
            throw new ApiException(401, "Firma de webhook invalida");
        }
    }

    private String configuredCheckoutUrl(String reference) {
        String base = config.get(type().name() + "_CHECKOUT_URL", "");
        if (base.isBlank()) {
            return null;
        }
        return base + (base.contains("?") ? "&" : "?") + "reference=" + reference;
    }
}
