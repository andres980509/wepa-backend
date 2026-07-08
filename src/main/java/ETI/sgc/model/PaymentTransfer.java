package ETI.sgc.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentTransfer {
    public Long id;
    public Long obligacion_id;
    public Long usuario_id;
    public Long integrante_id;
    public BigDecimal amount;
    public String currency;
    public String method;
    public String bank;
    public String reference;
    public String status;
    public String support_path;
    public String support_original_filename;
    public String support_mime_type;
    public Long support_size_bytes;
    public String receipt_number;
    public String receipt_path;
    public String observations;
    public String rejection_reason;
    public Long movement_id;
    public String reviewed_by;
    public LocalDateTime reviewed_at;
    public LocalDateTime submitted_at;
}
