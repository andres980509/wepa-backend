package ETI.sgc.payment.receipt;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentReceiptData {
    public String receiptNumber;
    public LocalDateTime issuedAt = LocalDateTime.now();
    public String payerName;
    public String documentType;
    public String documentNumber;
    public String contact;
    public String concept;
    public String description;
    public BigDecimal amount;
    public String currency = "COP";
    public String reference;
    public String method;
    public String status = "VALIDADO";
    public String source;
    public String reviewedBy;
    public String notes;
}
