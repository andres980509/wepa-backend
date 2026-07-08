package ETI.sgc.payment;

public class WebhookResult {
    public final String providerReference;
    public final PaymentStatus status;
    public final String rawStatus;

    public WebhookResult(String providerReference, PaymentStatus status, String rawStatus) {
        this.providerReference = providerReference;
        this.status = status;
        this.rawStatus = rawStatus;
    }
}
