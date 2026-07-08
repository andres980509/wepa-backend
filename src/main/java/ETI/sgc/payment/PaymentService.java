package ETI.sgc.payment;

import ETI.sgc.audit.AuditDAO;
import ETI.sgc.dao.ObligacionDAO;
import ETI.sgc.error.ApiException;
import ETI.sgc.model.Obligacion;
import ETI.sgc.payment.receipt.GeneratedReceipt;
import ETI.sgc.payment.receipt.PaymentReceiptData;
import ETI.sgc.payment.receipt.PaymentReceiptService;
import ETI.sgc.payment.providers.PaymentProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class PaymentService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PaymentDAO paymentDAO;
    private final ObligacionDAO obligacionDAO;
    private final AuditDAO auditDAO;
    private final PaymentReceiptService receiptService;
    private final Map<PaymentProviderType, PaymentProvider> providers = new HashMap<>();

    public PaymentService(PaymentDAO paymentDAO, ObligacionDAO obligacionDAO, PaymentProvider... providers) {
        this(paymentDAO, obligacionDAO, null, null, providers);
    }

    public PaymentService(PaymentDAO paymentDAO, ObligacionDAO obligacionDAO, AuditDAO auditDAO, PaymentProvider... providers) {
        this(paymentDAO, obligacionDAO, auditDAO, null, providers);
    }

    public PaymentService(
            PaymentDAO paymentDAO,
            ObligacionDAO obligacionDAO,
            AuditDAO auditDAO,
            PaymentReceiptService receiptService,
            PaymentProvider... providers
    ) {
        this.paymentDAO = paymentDAO;
        this.obligacionDAO = obligacionDAO;
        this.auditDAO = auditDAO;
        this.receiptService = receiptService;
        for (PaymentProvider provider : providers) {
            this.providers.put(provider.type(), provider);
        }
    }

    public Map<String, Object> initiate(PaymentInitRequest request) {
        return initiate(request, null, null);
    }

    public Map<String, Object> initiate(PaymentInitRequest request, String username, String ip) {
        validate(request);
        return createTransaction(request, username, ip);
    }

    public Map<String, Object> initiateForIntegrante(PaymentInitRequest request, Long integranteId, String username, String ip) {
        validate(request, integranteId);
        return createTransaction(request, username, ip);
    }

    private Map<String, Object> createTransaction(PaymentInitRequest request, String username, String ip) {
        PaymentProviderType providerType = parseProvider(request.provider);
        PaymentProvider provider = provider(providerType);

        try {
            enrichCheckoutTitle(request);
            String rawRequest = MAPPER.writeValueAsString(request);
            Long localId = paymentDAO.createPending(request, providerType, rawRequest);
            PaymentProviderResponse response = provider.createPayment(localId, request);
            paymentDAO.attachProviderResponse(localId, response);
            audit(username, ip, "CREAR_PAGO", "PAGOS", "payment_transactions", String.valueOf(localId), null,
                    Map.of("provider", providerType.name(), "obligacion_id", request.obligacion_id, "amount", request.amount));

            return Map.of(
                    "id", localId,
                    "provider", providerType.name(),
                    "provider_reference", response.providerReference,
                    "provider_preference_id", response.providerPreferenceId == null ? "" : response.providerPreferenceId,
                    "checkout_url", response.checkoutUrl == null ? "" : response.checkoutUrl,
                    "amount", request.amount,
                    "currency", request.currency == null || request.currency.isBlank() ? "COP" : request.currency.trim().toUpperCase(),
                    "status", response.status.name(),
                    "expires_at", response.expiresAt == null ? "" : response.expiresAt.toString(),
                    "message", response.message
            );
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "No se pudo iniciar el pago");
        }
    }

    public Map<String, Object> processWebhook(PaymentProviderType providerType, Context ctx, String rawBody) {
        PaymentProvider provider = provider(providerType);
        WebhookResult result = provider.parseAndValidateWebhook(ctx, rawBody);
        paymentDAO.insertEvent(providerType, result.providerReference, "WEBHOOK", rawBody);

        Map<String, Object> existing = paymentDAO.findByProviderReference(result.providerReference);
        if (existing == null) {
            throw new ApiException(404, "Transaccion local no encontrada");
        }

        String previousStatus = String.valueOf(existing.get("status"));
        if ("EXPIRADO".equalsIgnoreCase(previousStatus)) {
            audit(providerType.name() + "_WEBHOOK", ctx.ip(), "WEBHOOK_IGNORADO_LINK_EXPIRADO", "PAGOS", "payment_transactions",
                    String.valueOf(existing.get("id")), existing,
                    Map.of("incoming_status", result.status.name(), "provider_status", result.rawStatus == null ? "" : result.rawStatus));
            return Map.of(
                    "provider_reference", result.providerReference,
                    "status", previousStatus,
                    "incoming_status", result.status.name(),
                    "ignored", true,
                    "message", "Link expirado manualmente; webhook no aplicado"
            );
        }

        paymentDAO.updateStatus(result.providerReference, result.status, result.rawStatus);
        audit(providerType.name() + "_WEBHOOK", ctx.ip(), "WEBHOOK", "PAGOS", "payment_transactions",
                String.valueOf(existing.get("id")), existing,
                Map.of("status", result.status.name(), "provider_status", result.rawStatus == null ? "" : result.rawStatus));

        if (result.status == PaymentStatus.APROBADO && !"APROBADO".equalsIgnoreCase(previousStatus)) {
            Long obligacionId = ((Number) existing.get("obligacion_id")).longValue();
            BigDecimal amount = paymentDAO.amountFrom(existing);
            Long movementId = obligacionDAO.registrarPagoPasarela(
                    obligacionId,
                    amount,
                    "Pago aprobado por " + providerType.name() + " ref " + result.providerReference,
                    result.providerReference
            );
            attachReceiptForGatewayPayment(obligacionId, movementId, amount, providerType, result.providerReference);
        }

        return Map.of(
                "provider_reference", result.providerReference,
                "status", result.status.name(),
                "previous_status", previousStatus
        );
    }

    public Object listByObligacion(Long obligacionId) {
        return paymentDAO.listByObligacion(obligacionId);
    }

    public Map<String, Object> cleanupPending(PaymentCleanupRequest request, String username, String ip) {
        PaymentProviderType providerType = request == null || request.provider == null || request.provider.isBlank()
                ? null
                : parseProvider(request.provider);
        Long obligationId = request == null ? null : request.obligacion_id;
        int olderThanMinutes = request == null || request.older_than_minutes == null ? 0 : request.older_than_minutes;
        if (olderThanMinutes < 0) {
            throw new ApiException(400, "older_than_minutes no puede ser negativo");
        }

        List<Map<String, Object>> pendingTransactions = paymentDAO.findPendingForCleanup(obligationId, providerType, olderThanMinutes);
        List<Long> ids = new ArrayList<>();
        int providerClosed = 0;
        int providerSkipped = 0;
        int providerFailed = 0;

        for (Map<String, Object> transaction : pendingTransactions) {
            ids.add(((Number) transaction.get("id")).longValue());
            try {
                PaymentProviderType transactionProvider = parseProvider(String.valueOf(transaction.get("provider")));
                boolean closed = provider(transactionProvider).expirePaymentLink(transaction);
                if (closed) {
                    providerClosed++;
                } else {
                    providerSkipped++;
                }
            } catch (Exception e) {
                providerFailed++;
            }
        }

        String cleanupMessage = cleanupMessage(providerClosed, providerSkipped, providerFailed);
        int expired = paymentDAO.expirePendingByIds(ids, cleanupMessage);
        audit(username, ip, "LIMPIAR_LINKS_PAGO", "PAGOS", "payment_transactions",
                obligationId == null ? "ALL" : String.valueOf(obligationId), null,
                Map.of(
                        "expired", expired,
                        "provider_closed", providerClosed,
                        "provider_skipped", providerSkipped,
                        "provider_failed", providerFailed,
                        "obligacion_id", obligationId == null ? "" : obligationId,
                        "provider", providerType == null ? "" : providerType.name(),
                        "older_than_minutes", olderThanMinutes
                ));

        return Map.of(
                "expired", expired,
                "status", "OK",
                "message", expired == 1
                        ? "1 link pendiente marcado como expirado. " + cleanupMessage
                        : expired + " links pendientes marcados como expirados. " + cleanupMessage
        );
    }

    public Map<String, Object> reverse(Long id, String reason, String username, String ip) {
        Map<String, Object> transaction = paymentDAO.findById(id);
        if (transaction == null) {
            throw new ApiException(404, "Transaccion no encontrada");
        }

        PaymentProviderType providerType = parseProvider(String.valueOf(transaction.get("provider")));
        PaymentProviderResponse response = provider(providerType).reversePayment(transaction, reason);
        paymentDAO.updateStatusById(id, response.status, "REVERSE_REQUESTED", response.message);
        audit(username, ip, "REVERSO", "PAGOS", "payment_transactions", String.valueOf(id), transaction,
                Map.of("status", response.status.name(), "reason", reason == null ? "" : reason));

        return Map.of(
                "id", id,
                "provider", providerType.name(),
                "status", response.status.name(),
                "message", response.message
        );
    }

    private void validate(PaymentInitRequest request) {
        validate(request, null);
    }

    private void validate(PaymentInitRequest request, Long integranteId) {
        if (request == null) {
            throw new ApiException(400, "Solicitud de pago requerida");
        }
        if (request.provider == null || request.provider.isBlank()) {
            throw new ApiException(400, "Proveedor de pago requerido");
        }
        if (request.obligacion_id == null) {
            throw new ApiException(400, "obligacion_id requerido");
        }
        if (request.amount == null || request.amount.signum() <= 0) {
            throw new ApiException(400, "Monto invalido");
        }
        Obligacion obligacion = obligacionDAO.obtenerConBloqueo(request.obligacion_id);
        if (obligacion == null) {
            throw new ApiException(404, "Obligacion no encontrada");
        }
        if (integranteId != null && (!"INTEGRANTE".equalsIgnoreCase(obligacion.getTipo_tercero())
                || obligacion.getTercero_id() == null
                || !obligacion.getTercero_id().equals(integranteId))) {
            throw new ApiException(403, "No puede pagar obligaciones de otro integrante");
        }
        if ("PAGADO".equalsIgnoreCase(obligacion.getEstado()) || obligacion.getSaldo() == null || obligacion.getSaldo().signum() <= 0) {
            throw new ApiException(400, "La obligacion ya esta pagada");
        }
        if (request.amount.compareTo(obligacion.getSaldo()) > 0) {
            throw new ApiException(400, "El monto no puede superar el saldo pendiente");
        }
    }

    private PaymentProvider provider(PaymentProviderType providerType) {
        PaymentProvider provider = providers.get(providerType);
        if (provider == null) {
            throw new ApiException(400, "Proveedor no configurado: " + providerType);
        }
        return provider;
    }

    private void enrichCheckoutTitle(PaymentInitRequest request) {
        if (request.item_title != null && !request.item_title.isBlank()) {
            request.item_title = sanitizeTitle(request.item_title);
            return;
        }

        Map<String, Object> row = obligacionDAO.obtenerDatosRecibo(request.obligacion_id);
        String concept = text(row.get("concepto_nombre"));
        String description = text(row.get("obligacion_descripcion"));
        String title = firstNonBlank(concept, description, "Obligacion");
        request.item_title = sanitizeTitle("WEPA - " + title);
    }

    private String sanitizeTitle(String value) {
        String text = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (text.isBlank()) {
            return "WEPA - Obligacion";
        }
        return text.length() > 120 ? text.substring(0, 120) : text;
    }

    private PaymentProviderType parseProvider(String provider) {
        try {
            return PaymentProviderType.valueOf(provider.trim().toUpperCase());
        } catch (Exception e) {
            throw new ApiException(400, "Proveedor invalido. Use WOMPI, EPAYCO o MERCADOPAGO");
        }
    }

    private void attachReceiptForGatewayPayment(
            Long obligacionId,
            Long movementId,
            BigDecimal amount,
            PaymentProviderType providerType,
            String providerReference
    ) {
        if (receiptService == null || movementId == null) {
            return;
        }

        Map<String, Object> row = obligacionDAO.obtenerDatosRecibo(obligacionId);
        PaymentReceiptData data = new PaymentReceiptData();
        data.receiptNumber = receiptService.newReceiptNumber(providerType.name(), providerReference);
        data.issuedAt = LocalDateTime.now();
        data.payerName = text(row.get("tercero_nombre"));
        data.documentType = text(row.get("tipo_documento"));
        data.documentNumber = text(row.get("numero_documento"));
        data.contact = join(text(row.get("telefono")), text(row.get("correo")));
        data.concept = text(row.get("concepto_nombre"));
        data.description = text(row.get("obligacion_descripcion"));
        data.amount = amount;
        data.reference = providerReference;
        data.method = providerType.name();
        data.status = "VALIDADO";
        data.source = "PASARELA";
        data.reviewedBy = providerType.name() + "_WEBHOOK";
        data.notes = "Pago aprobado automaticamente por webhook";

        GeneratedReceipt receipt = receiptService.generate(data);
        obligacionDAO.registrarReciboSistemaDocumento(
                movementId,
                receipt.filename,
                receipt.relativePath,
                providerType.name() + "_WEBHOOK"
        );
    }

    private String join(String left, String right) {
        if (left.isBlank()) return right;
        if (right.isBlank()) return left;
        return left + " / " + right;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String cleanupMessage(int providerClosed, int providerSkipped, int providerFailed) {
        List<String> parts = new ArrayList<>();
        if (providerClosed > 0) {
            parts.add(providerClosed + " cerrados tambien en la pasarela");
        }
        if (providerSkipped > 0) {
            parts.add(providerSkipped + " sin cierre remoto disponible");
        }
        if (providerFailed > 0) {
            parts.add(providerFailed + " con error al cerrar en pasarela");
        }
        return parts.isEmpty() ? "No habia links remotos para cerrar" : String.join("; ", parts);
    }

    private String text(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    private void audit(String username, String ip, String action, String module, String entity, String entityId, Object before, Object after) {
        if (auditDAO == null) {
            return;
        }
        try {
            auditDAO.insert(username, ip, action, module, entity, entityId, toJson(before), toJson(after));
        } catch (Exception ignored) {
            // La auditoria no debe interrumpir pagos ni webhooks.
        }
    }

    private String toJson(Object value) throws Exception {
        if (value == null) {
            return "{}";
        }
        return MAPPER.writeValueAsString(value);
    }
}
