package ETI.sgc.payment.providers;

import ETI.sgc.config.AppConfig;
import ETI.sgc.payment.PaymentProviderType;

public class EpaycoPaymentProvider extends AbstractPaymentProvider {
    public EpaycoPaymentProvider(AppConfig config) {
        super(config);
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.EPAYCO;
    }
}
