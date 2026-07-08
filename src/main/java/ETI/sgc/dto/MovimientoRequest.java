package ETI.sgc.dto;

import java.math.BigDecimal;

public class MovimientoRequest {
    public Long obligacion_id;
    public String tipo; // INGRESO / EGRESO
    public BigDecimal monto;
    public String descripcion;
}