package ETI.sgc.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Obligacion {
    private Long id;
    private Long concepto_id;
    private String tipo_tercero;
    private Long tercero_id;
    private BigDecimal monto_total;
    private BigDecimal saldo;
    private String estado;
    private String descripcion;

    private String created_at; // <--- AÑADE ESTO
    // Constructor vacío obligatorio para JDBI
    public Obligacion() {}

    // Getters y Setters (Esto habilitará el uso de .getEstado() en el controlador)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Long getConcepto_id() { return concepto_id; }
    public void setConcepto_id(Long concepto_id) { this.concepto_id = concepto_id; }

    public String getTipo_tercero() { return tipo_tercero; }
    public void setTipo_tercero(String tipo_tercero) { this.tipo_tercero = tipo_tercero; }

    public Long getTercero_id() { return tercero_id; }
    public void setTercero_id(Long tercero_id) { this.tercero_id = tercero_id; }

    public BigDecimal getMonto_total() { return monto_total; }
    public void setMonto_total(BigDecimal monto_total) { this.monto_total = monto_total; }

    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }


    public String getCreated_at() { return created_at; }
    public void setCreated_at(String created_at) { this.created_at = created_at; }
}