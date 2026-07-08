package ETI.sgc.dto;

import java.math.BigDecimal;

public class RegistroPagoDTO {
    private final Long obligacionId;
    private final BigDecimal monto;
    private final String tipo;
    private final String descripcion;
    private final String metodoPago;
    private final String comprobanteNombre;
    private final String comprobanteRutaUrl;
    private final String reciboSistemaNombre;
    private final String reciboSistemaRutaUrl;
    private final String uploadedBy;

    // Constructor completo
    public RegistroPagoDTO(Long obligacionId, BigDecimal monto, String tipo, String descripcion,
                           String metodoPago, String comprobanteNombre, String comprobanteRutaUrl,
                           String reciboSistemaNombre, String reciboSistemaRutaUrl, String uploadedBy) {
        this.obligacionId = obligacionId;
        this.monto = monto;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.metodoPago = metodoPago;
        this.comprobanteNombre = comprobanteNombre;
        this.comprobanteRutaUrl = comprobanteRutaUrl;
        this.reciboSistemaNombre = reciboSistemaNombre;
        this.reciboSistemaRutaUrl = reciboSistemaRutaUrl;
        this.uploadedBy = uploadedBy;
    }

    // Getters tradicionales
    public Long getObligacionId() { return obligacionId; }
    public BigDecimal getMonto() { return monto; }
    public String getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public String getMetodoPago() { return metodoPago; }
    public String getComprobanteNombre() { return comprobanteNombre; }
    public String getComprobanteRutaUrl() { return comprobanteRutaUrl; }
    public String getReciboSistemaNombre() { return reciboSistemaNombre; }
    public String getReciboSistemaRutaUrl() { return reciboSistemaRutaUrl; }
    public String getUploadedBy() { return uploadedBy; }
}
