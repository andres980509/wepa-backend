package ETI.sgc.payment;

import java.time.OffsetDateTime;

public class PaymentProviderResponse {
    public final String providerReference;
    public final String providerPreferenceId;
    public final String checkoutUrl;
    public final PaymentStatus status;
    public final String message;
    public final OffsetDateTime expiresAt;

    public PaymentProviderResponse(String providerReference, String checkoutUrl, PaymentStatus status, String message) {
        this(providerReference, null, checkoutUrl, status, message, null);
    }

    public PaymentProviderResponse(
            String providerReference,
            String providerPreferenceId,
            String checkoutUrl,
            PaymentStatus status,
            String message,
            OffsetDateTime expiresAt
    ) {
        this.providerReference = providerReference;
        this.providerPreferenceId = providerPreferenceId;
        this.checkoutUrl = checkoutUrl;
        this.status = status;
        this.message = message;
        this.expiresAt = expiresAt;
    }
}
