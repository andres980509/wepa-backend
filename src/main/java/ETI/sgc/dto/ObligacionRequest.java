package ETI.sgc.dto;

import java.math.BigDecimal;

public class ObligacionRequest {
    public Long concepto_id;
    public String tipo_tercero;
    public Long tercero_id;
    public BigDecimal monto_total;
    public String descripcion;
}