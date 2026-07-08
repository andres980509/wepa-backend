package ETI.sgc.payment.providers;

import ETI.sgc.config.AppConfig;
import ETI.sgc.payment.PaymentProviderType;

public class WompiPaymentProvider extends AbstractPaymentProvider {
    public WompiPaymentProvider(AppConfig config) {
        super(config);
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.WOMPI;
    }
}
