package ETI.sgc.dto;

import java.math.BigDecimal;
import java.util.List;

public class ObligacionMasivaRequest {
    public Long concepto_id;
    public String tipo_tercero;
    public List<Long> terceros;
    public BigDecimal monto_total;
    public String descripcion;
}