package ETI.sgc.payment.providers;

import ETI.sgc.config.AppConfig;
import ETI.sgc.error.ApiException;
import ETI.sgc.payment.PaymentInitRequest;
import ETI.sgc.payment.PaymentProviderResponse;
import ETI.sgc.payment.PaymentProviderType;
import ETI.sgc.payment.PaymentStatus;
import ETI.sgc.payment.WebhookResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MercadoPagoPaymentProvider extends AbstractPaymentProvider {
    private static final DateTimeFormatter MP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public MercadoPagoPaymentProvider(AppConfig config) {
        super(config);
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.MERCADOPAGO;
    }

    @Override
    public PaymentProviderResponse createPayment(Long localTransactionId, PaymentInitRequest request) {
        String accessToken = requiredAccessToken();
        String reference = "mercadopago-" + localTransactionId + "-" + UUID.randomUUID();

        try {
            ObjectNode payload = MAPPER.createObjectNode();
            ArrayNode items = payload.putArray("items");
            ObjectNode item = items.addObject();
            item.put("id", String.valueOf(request.obligacion_id));
            item.put("title", firstNonBlank(request.item_title, "WEPA - Obligacion"));
            item.put("quantity", 1);
            item.put("currency_id", request.currency == null || request.currency.isBlank() ? "COP" : request.currency.trim().toUpperCase(Locale.ROOT));
            item.put("unit_price", money(request.amount));

            payload.put("external_reference", reference);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime expiresAt = now.plusMinutes(linkExpirationMinutes(request.method));
            payload.put("expires", true);
            payload.put("expiration_date_from", MP_DATE_FORMAT.format(now.minusSeconds(30)));
            payload.put("expiration_date_to", MP_DATE_FORMAT.format(expiresAt));

            ObjectNode payer = payload.putObject("payer");
            if (request.payer_email != null && !request.payer_email.isBlank()) {
                payer.put("email", request.payer_email.trim());
            }
            if (request.payer_document != null && !request.payer_document.isBlank()) {
                ObjectNode identification = payer.putObject("identification");
                identification.put("type", "CC");
                identification.put("number", request.payer_document.trim());
            }

            ObjectNode paymentMethods = payload.putObject("payment_methods");
            if ("PSE".equalsIgnoreCase(request.method)) {
                paymentMethods.putArray("excluded_payment_types")
                        .addObject()
                        .put("id", "credit_card");
            }

            String notificationUrl = notificationUrl();
            if (!notificationUrl.isBlank()) {
                payload.put("notification_url", notificationUrl);
            }

            ObjectNode backUrls = MAPPER.createObjectNode();
            putIfPresent(backUrls, "success", config.get("MERCADOPAGO_SUCCESS_URL", ""));
            putIfPresent(backUrls, "failure", config.get("MERCADOPAGO_FAILURE_URL", ""));
            putIfPresent(backUrls, "pending", config.get("MERCADOPAGO_PENDING_URL", ""));
            if (!backUrls.isEmpty()) {
                payload.set("back_urls", backUrls);
                if (backUrls.has("success") && config.getBoolean("MERCADOPAGO_AUTO_RETURN_ENABLED", false)) {
                    payload.put("auto_return", "approved");
                }
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase() + "/checkout/preferences"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(502, "MercadoPago no pudo crear la preferencia: " + response.body());
            }

            JsonNode json = MAPPER.readTree(response.body());
            String checkoutUrl = checkoutUrl(json);
            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                throw new ApiException(502, "MercadoPago no retorno URL de checkout");
            }

            String preferenceId = json.path("id").asText(null);
            return new PaymentProviderResponse(
                    reference,
                    preferenceId,
                    checkoutUrl,
                    PaymentStatus.PENDIENTE,
                    "Preferencia MercadoPago creada. Vence en " + MP_DATE_FORMAT.format(expiresAt),
                    expiresAt
            );
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "No se pudo crear el checkout de MercadoPago");
        }
    }

    @Override
    public boolean expirePaymentLink(Map<String, Object> transaction) {
        String preferenceId = preferenceId(transaction);
        if (preferenceId == null || preferenceId.isBlank()) {
            return false;
        }

        ObjectNode payload = MAPPER.createObjectNode();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusMinutes(cleanupExpirationMinutes());
        payload.put("expires", true);
        payload.put("expiration_date_from", MP_DATE_FORMAT.format(now.minusSeconds(30)));
        payload.put("expiration_date_to", MP_DATE_FORMAT.format(expiresAt));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase() + "/checkout/preferences/" + URLEncoder.encode(preferenceId, StandardCharsets.UTF_8)))
                    .header("Authorization", "Bearer " + requiredAccessToken())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return false;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(502, "MercadoPago no pudo vencer la preferencia: " + response.body());
            }
            return true;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "No se pudo vencer preferencia MercadoPago");
        }
    }

    @Override
    public WebhookResult parseAndValidateWebhook(Context ctx, String rawBody) {
        try {
            JsonNode json = MAPPER.readTree(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
            validateOfficialSignature(ctx, json, rawBody);

            String dataId = firstNonBlank(ctx.queryParam("data.id"), json.at("/data/id").asText(null), findText(json, "id"));
            JsonNode payment = fetchPaymentIfPossible(dataId);
            if (payment != null && !payment.isMissingNode()) {
                String reference = payment.path("external_reference").asText(null);
                String status = payment.path("status").asText(null);
                if (reference == null || reference.isBlank()) {
                    throw new ApiException(400, "Pago MercadoPago sin external_reference");
                }
                return new WebhookResult(reference, mapStatus(status), status);
            }

            String reference = findText(json,
                    "external_reference",
                    "reference",
                    "provider_reference",
                    "transaction_id",
                    "id"
            );
            if (reference == null || reference.isBlank()) {
                reference = firstNonBlank(ctx.queryParam("external_reference"), ctx.queryParam("reference"));
            }

            String status = findText(json, "status", "estado", "transaction_status");
            if (status == null || status.isBlank()) {
                status = findText(json, "action", "type");
            }

            if (reference == null || reference.isBlank()) {
                throw new ApiException(400, "Webhook MercadoPago sin referencia");
            }

            return new WebhookResult(reference, mapStatus(status), status);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(400, "Webhook MercadoPago invalido");
        }
    }

    @Override
    protected PaymentStatus mapStatus(String rawStatus) {
        if (rawStatus == null) {
            return PaymentStatus.PENDIENTE;
        }
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "approved", "payment.updated.approved", "paid" -> PaymentStatus.APROBADO;
            case "rejected", "refunded", "charged_back", "failed" -> PaymentStatus.RECHAZADO;
            case "cancelled", "canceled", "expired" -> PaymentStatus.EXPIRADO;
            default -> super.mapStatus(rawStatus);
        };
    }

    private void validateOfficialSignature(Context ctx, JsonNode json, String rawBody) {
        String secret = config.get("MERCADOPAGO_WEBHOOK_SECRET", "");
        if (secret.isBlank()) {
            return;
        }

        String signature = firstNonBlank(ctx.header("x-signature"), ctx.header("X-Signature"));
        String requestId = firstNonBlank(ctx.header("x-request-id"), ctx.header("X-Request-Id"));
        String dataId = firstNonBlank(ctx.queryParam("data.id"), json.at("/data/id").asText(null), findText(json, "id"));

        if (signature == null || requestId == null || dataId == null) {
            throw new ApiException(401, "Firma MercadoPago incompleta");
        }

        Map<String, String> parts = signatureParts(signature);
        String ts = parts.get("ts");
        String v1 = parts.get("v1");
        if (ts == null || v1 == null) {
            throw new ApiException(401, "Firma MercadoPago invalida");
        }

        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        String expected = hmacSha256(secret, manifest);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(401, "Firma MercadoPago invalida");
        }
    }

    private JsonNode fetchPaymentIfPossible(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            return null;
        }

        String accessToken = config.get("MERCADOPAGO_ACCESS_TOKEN", "");
        if (accessToken.isBlank()) {
            return null;
        }

        try {
            String encodedId = URLEncoder.encode(paymentId, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase() + "/v1/payments/" + encodedId))
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(502, "No se pudo consultar pago MercadoPago");
            }
            return MAPPER.readTree(response.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "No se pudo consultar pago MercadoPago");
        }
    }

    private String requiredAccessToken() {
        String token = config.get("MERCADOPAGO_ACCESS_TOKEN", "");
        if (token.isBlank()) {
            throw new ApiException(400, "MERCADOPAGO_ACCESS_TOKEN requerido para crear checkout MercadoPago");
        }
        return token.trim();
    }

    private String apiBase() {
        return config.get("MERCADOPAGO_API_BASE_URL", "https://api.mercadopago.com").replaceAll("/+$", "");
    }

    private String notificationUrl() {
        String explicit = config.get("MERCADOPAGO_NOTIFICATION_URL", "");
        if (!explicit.isBlank()) {
            return explicit.trim();
        }

        String publicBase = config.get("WEPA_PUBLIC_BASE_URL", "");
        if (publicBase.isBlank()) {
            return "";
        }
        return publicBase.replaceAll("/+$", "") + "/api/payments/webhooks/mercadopago";
    }

    private int linkExpirationMinutes(String method) {
        String key = "PSE".equalsIgnoreCase(method)
                ? "MERCADOPAGO_PSE_LINK_EXPIRATION_MINUTES"
                : "MERCADOPAGO_LINK_EXPIRATION_MINUTES";
        int minutes = config.getInt(key, config.getInt("MERCADOPAGO_LINK_EXPIRATION_MINUTES", 60));
        return Math.max(5, minutes);
    }

    private int cleanupExpirationMinutes() {
        return Math.max(1, config.getInt("MERCADOPAGO_CLEANUP_EXPIRATION_MINUTES", 5));
    }

    private String preferenceId(Map<String, Object> transaction) {
        Object stored = transaction.get("provider_preference_id");
        String preferenceId = stored == null ? "" : String.valueOf(stored).trim();
        if (!preferenceId.isBlank() && !"null".equalsIgnoreCase(preferenceId)) {
            return preferenceId;
        }

        Object checkout = transaction.get("checkout_url");
        if (checkout == null) {
            return "";
        }
        String checkoutUrl = String.valueOf(checkout);
        int marker = checkoutUrl.indexOf("pref_id=");
        if (marker < 0) {
            return "";
        }
        String value = checkoutUrl.substring(marker + "pref_id=".length());
        int separator = value.indexOf('&');
        return separator >= 0 ? value.substring(0, separator) : value;
    }

    private String checkoutUrl(JsonNode preference) {
        String mode = config.get("MERCADOPAGO_CHECKOUT_URL_MODE", "init_point").trim().toLowerCase(Locale.ROOT);
        String initPoint = preference.path("init_point").asText(null);
        String sandboxInitPoint = preference.path("sandbox_init_point").asText(null);

        return switch (mode) {
            case "sandbox", "sandbox_init_point" -> firstNonBlank(sandboxInitPoint, initPoint);
            case "auto" -> firstNonBlank(initPoint, sandboxInitPoint);
            default -> firstNonBlank(initPoint, sandboxInitPoint);
        };
    }

    private double money(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value.trim());
        }
    }

    private Map<String, String> signatureParts(String signature) {
        Map<String, String> parts = new HashMap<>();
        for (String piece : signature.split(",")) {
            String[] keyValue = piece.split("=", 2);
            if (keyValue.length == 2) {
                parts.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return parts;
    }

    private String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ApiException(500, "No se pudo validar firma MercadoPago");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
