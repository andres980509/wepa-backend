package ETI.sgc.payment;

import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class PaymentDAO {
    private final Jdbi jdbi;

    public PaymentDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Long createPending(PaymentInitRequest request, PaymentProviderType provider, String rawRequest) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                INSERT INTO payment_transactions (
                    provider, obligacion_id, amount, currency, method, payer_email,
                    payer_document, status, raw_request, created_at, updated_at
                )
                VALUES (
                    :provider, :obligacionId, :amount, :currency, :method, :payerEmail,
                    :payerDocument, 'PENDIENTE', CAST(:rawRequest AS jsonb), NOW(), NOW()
                )
                """)
                .bind("provider", provider.name())
                .bind("obligacionId", request.obligacion_id)
                .bind("amount", request.amount)
                .bind("currency", request.currency == null ? "COP" : request.currency)
                .bind("method", request.method)
                .bind("payerEmail", request.payer_email)
                .bind("payerDocument", request.payer_document)
                .bind("rawRequest", rawRequest)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one());
    }

    public void attachProviderResponse(Long id, PaymentProviderResponse response) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE payment_transactions
                SET provider_reference = :providerReference,
                    provider_preference_id = :providerPreferenceId,
                    checkout_url = :checkoutUrl,
                    status = :status,
                    provider_message = :message,
                    expires_at = :expiresAt,
                    updated_at = NOW()
                WHERE id = :id
                """)
                .bind("id", id)
                .bind("providerReference", response.providerReference)
                .bind("providerPreferenceId", response.providerPreferenceId)
                .bind("checkoutUrl", response.checkoutUrl)
                .bind("status", response.status.name())
                .bind("message", response.message)
                .bind("expiresAt", response.expiresAt)
                .execute());
    }

    public Map<String, Object> findByProviderReference(String providerReference) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT *
                FROM payment_transactions
                WHERE provider_reference = :providerReference
                """)
                .bind("providerReference", providerReference)
                .mapToMap()
                .findOne()
                .orElse(null));
    }

    public Map<String, Object> findById(Long id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT *
                FROM payment_transactions
                WHERE id = :id
                """)
                .bind("id", id)
                .mapToMap()
                .findOne()
                .orElse(null));
    }

    public void updateStatus(String providerReference, PaymentStatus status, String rawStatus) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE payment_transactions
                SET status = :status,
                    provider_status = :rawStatus,
                    updated_at = NOW()
                WHERE provider_reference = :providerReference
                """)
                .bind("providerReference", providerReference)
                .bind("status", status.name())
                .bind("rawStatus", rawStatus)
                .execute());
    }

    public void updateStatusById(Long id, PaymentStatus status, String rawStatus, String message) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE payment_transactions
                SET status = :status,
                    provider_status = :rawStatus,
                    provider_message = COALESCE(:message, provider_message),
                    updated_at = NOW()
                WHERE id = :id
                """)
                .bind("id", id)
                .bind("status", status.name())
                .bind("rawStatus", rawStatus)
                .bind("message", message)
                .execute());
    }

    public void insertEvent(PaymentProviderType provider, String providerReference, String eventType, String payload) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO payment_events (provider, provider_reference, event_type, payload, created_at)
                VALUES (:provider, :providerReference, :eventType, CAST(:payload AS jsonb), NOW())
                """)
                .bind("provider", provider.name())
                .bind("providerReference", providerReference)
                .bind("eventType", eventType)
                .bind("payload", payload == null || payload.isBlank() ? "{}" : payload)
                .execute());
    }

    public List<Map<String, Object>> listByObligacion(Long obligacionId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT *
                FROM payment_transactions
                WHERE obligacion_id = :obligacionId
                ORDER BY created_at DESC
                """)
                .bind("obligacionId", obligacionId)
                .mapToMap()
                .list());
    }

    public List<Map<String, Object>> findPendingForCleanup(Long obligacionId, PaymentProviderType provider, int olderThanMinutes) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT *
                FROM payment_transactions
                WHERE status = 'PENDIENTE'
                  AND (:obligacionId IS NULL OR obligacion_id = :obligacionId)
                  AND (:provider IS NULL OR provider = :provider)
                  AND (:olderThanMinutes = 0 OR created_at <= NOW() - (:olderThanMinutes * INTERVAL '1 minute'))
                ORDER BY created_at ASC
                """)
                .bind("obligacionId", obligacionId)
                .bind("provider", provider == null ? null : provider.name())
                .bind("olderThanMinutes", olderThanMinutes)
                .mapToMap()
                .list());
    }

    public int expirePendingByIds(List<Long> ids, String message) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        return jdbi.withHandle(handle -> handle.createUpdate("""
                UPDATE payment_transactions
                SET status = 'EXPIRADO',
                    provider_status = 'MANUAL_CLEANUP',
                    provider_message = :message,
                    updated_at = NOW()
                WHERE id IN (<ids>)
                  AND status = 'PENDIENTE'
                """)
                .bindList("ids", ids)
                .bind("message", message)
                .execute());
    }

    public int expirePending(Long obligacionId, PaymentProviderType provider, int olderThanMinutes, String message) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                UPDATE payment_transactions
                SET status = 'EXPIRADO',
                    provider_status = 'MANUAL_CLEANUP',
                    provider_message = :message,
                    updated_at = NOW()
                WHERE status = 'PENDIENTE'
                  AND (:obligacionId IS NULL OR obligacion_id = :obligacionId)
                  AND (:provider IS NULL OR provider = :provider)
                  AND (:olderThanMinutes = 0 OR created_at <= NOW() - (:olderThanMinutes * INTERVAL '1 minute'))
                """)
                .bind("obligacionId", obligacionId)
                .bind("provider", provider == null ? null : provider.name())
                .bind("olderThanMinutes", olderThanMinutes)
                .bind("message", message)
                .execute());
    }

    public BigDecimal amountFrom(Map<String, Object> row) {
        Object value = row.get("amount");
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
