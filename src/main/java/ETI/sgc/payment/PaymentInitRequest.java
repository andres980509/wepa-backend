package ETI.sgc.payment;

import java.math.BigDecimal;

public class PaymentInitRequest {
    public String provider;
    public Long obligacion_id;
    public BigDecimal amount;
    public String currency;
    public String method;
    public String payer_email;
    public String payer_document;
    public String return_url;
    public String item_title;
}
