package ETI.sgc.payment.providers;

import ETI.sgc.payment.PaymentInitRequest;
import ETI.sgc.payment.PaymentProviderResponse;
import ETI.sgc.payment.PaymentProviderType;
import ETI.sgc.payment.PaymentStatus;
import ETI.sgc.payment.WebhookResult;
import io.javalin.http.Context;

import java.util.Map;

public interface PaymentProvider {
    PaymentProviderType type();

    PaymentProviderResponse createPayment(Long localTransactionId, PaymentInitRequest request);

    WebhookResult parseAndValidateWebhook(Context ctx, String rawBody);

    default boolean expirePaymentLink(Map<String, Object> transaction) {
        return false;
    }

    default PaymentProviderResponse reversePayment(Map<String, Object> transaction, String reason) {
        String reference = String.valueOf(transaction.get("provider_reference"));
        return new PaymentProviderResponse(
                reference,
                null,
                PaymentStatus.RECHAZADO,
                "Reverso registrado localmente. Integracion API de reverso pendiente para " + type().name()
        );
    }
}
