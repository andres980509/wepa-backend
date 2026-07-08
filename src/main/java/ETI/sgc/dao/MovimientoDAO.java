package ETI.sgc.dao;

import org.jdbi.v3.core.Jdbi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class MovimientoDAO {
    private final Jdbi jdbi;

    public MovimientoDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Long crear(Long obligacionId, String tipo, BigDecimal monto, String descripcion) {
        return jdbi.withHandle(h ->
                h.createUpdate("""
            INSERT INTO movimientos (obligacion_id, tipo, monto, descripcion)
            VALUES (:o, :t::tipo_movimiento_enum, :m, :d)
        """)
                        .bind("o", obligacionId)
                        .bind("t", tipo) // Viene 'EGRESO' del front
                        .bind("m", monto)
                        .bind("d", descripcion)
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Map<String, Object>> listarPorTercero(Long terceroId, String tipoTercero) {
        return jdbi.withHandle(h -> {
            String query = """
        SELECT 
            m.id, 
            m.descripcion, 
            m.monto, 
            m.created_at,
            o.descripcion as obligacion_nombre, 
            o.tipo_tercero,
            COALESCE(
                (SELECT json_agg(json_build_object(
                    'id', d.id,
                    'nombre', d.nombre_archivo,
                    'ruta_url', d.ruta_url,
                    'tipo_documento', d.tipo_documento
                ))
                 FROM documentos d
                 WHERE d.entidad_id = m.id AND d.entidad_tipo = 'MOVIMIENTO'
                ), '[]'::json
            )::text as documentos -- <--- Forzamos el casteo a texto plano
        FROM movimientos m
        JOIN obligaciones o ON m.obligacion_id = o.id
        WHERE o.tercero_id = :terceroId 
          AND o.tipo_tercero = :tipoTercero::tipo_tercero_enum
        ORDER BY m.created_at DESC
        """;

            return h.createQuery(query)
                    .bind("terceroId", terceroId)
                    .bind("tipoTercero", tipoTercero)
                    .mapToMap()
                    .list();
        });
    }

    // --- MÉTODO 1: PARA PAGOS DE INTEGRANTES ---
    public void reversarAbonoIntegrante(Long movimientoId, String motivo) {
        jdbi.useTransaction(handle -> {
            // 1. Obtener y bloquear el movimiento
            var mov = handle.createQuery("SELECT * FROM movimientos WHERE id = :id FOR UPDATE")
                    .bind("id", movimientoId)
                    .mapToMap()
                    .findOne()
                    .orElseThrow(() -> new RuntimeException("Movimiento no encontrado"));

            if (mov.get("descripcion").toString().contains("REVERSO")) {
                throw new RuntimeException("No se puede reversar un movimiento que ya es una reversión");
            }

            BigDecimal monto = (BigDecimal) mov.get("monto");
            Long obId = ((Number) mov.get("obligacion_id")).longValue();
            String tipoOriginal = mov.get("tipo").toString().trim().toUpperCase();

            // 2. Insertar movimiento de ajuste (espejo)
            String tipoReversa = tipoOriginal.equals("INGRESO") ? "EGRESO" : "INGRESO";
            handle.createUpdate("""
            INSERT INTO movimientos (obligacion_id, tipo, monto, descripcion)
            VALUES (:obId, :tipo::tipo_movimiento_enum, :monto, :desc)
        """)
                    .bind("obId", obId)
                    .bind("tipo", tipoReversa)
                    .bind("monto", monto)
                    .bind("desc", "REVERSO ABONO: " + motivo)
                    .execute();

            // 3. LÓGICA INTEGRANTE: Si el original fue INGRESO (abono), el saldo DEBE SUBIR (+)
            if (tipoOriginal.equals("INGRESO")) {
                handle.createUpdate("UPDATE obligaciones SET saldo = saldo + :monto, estado = 'PENDIENTE' WHERE id = :id")
                        .bind("monto", monto)
                        .bind("id", obId)
                        .execute();
            } else {
                // Si fue un cargo manual (Egreso), el reverso BAJA (-) el saldo
                handle.createUpdate("UPDATE obligaciones SET saldo = saldo - :monto, estado = 'PENDIENTE' WHERE id = :id")
                        .bind("monto", monto)
                        .bind("id", obId)
                        .execute();
            }
        });
    }

    // --- MÉTODO 2: PARA OBLIGACIONES DE GASTO (PROVEEDORES/EMPRESA) ---
    public void reversarGastoEmpresa(Long movimientoId, String motivo) {
        jdbi.useTransaction(handle -> {
            var mov = handle.createQuery("SELECT * FROM movimientos WHERE id = :id FOR UPDATE")
                    .bind("id", movimientoId)
                    .mapToMap()
                    .findOne()
                    .orElseThrow(() -> new RuntimeException("Movimiento no encontrado"));

            if (mov.get("descripcion").toString().contains("REVERSO")) {
                throw new RuntimeException("No se puede reversar un movimiento que ya es una reversión");
            }

            BigDecimal monto = (BigDecimal) mov.get("monto");
            Long obId = ((Number) mov.get("obligacion_id")).longValue();
            String tipoOriginal = mov.get("tipo").toString().trim().toUpperCase();

            String tipoReversa = tipoOriginal.equals("INGRESO") ? "EGRESO" : "INGRESO";
            handle.createUpdate("""
            INSERT INTO movimientos (obligacion_id, tipo, monto, descripcion)
            VALUES (:obId, :tipo::tipo_movimiento_enum, :monto, :desc)
        """)
                    .bind("obId", obId)
                    .bind("tipo", tipoReversa)
                    .bind("monto", monto)
                    .bind("desc", "REVERSO GASTO: " + motivo)
                    .execute();

            // 3. LÓGICA GASTO: Si el original fue EGRESO (pago a proveedor), el saldo DEBE SUBIR (+)
            if (tipoOriginal.equals("EGRESO")) {
                handle.createUpdate("UPDATE obligaciones SET saldo = saldo + :monto, estado = 'PENDIENTE' WHERE id = :id")
                        .bind("monto", monto)
                        .bind("id", obId)
                        .execute();
            } else {
                // Si fue un ingreso, el reverso BAJA (-) el saldo
                handle.createUpdate("UPDATE obligaciones SET saldo = saldo - :monto, estado = 'PENDIENTE' WHERE id = :id")
                        .bind("monto", monto)
                        .bind("id", obId)
                        .execute();
            }
        });
    }
    public List<Map<String, Object>> listarConFiltros(String fechaInicio, String fechaFin, String tipo) {
        return jdbi.withHandle(h -> {
            // CORRECCIÓN: Usamos 'tipo_tercero' que es el nombre real de la columna
            String query = """
            SELECT m.*, o.descripcion as obligacion_nombre, o.tipo_tercero
            FROM movimientos m
            JOIN obligaciones o ON m.obligacion_id = o.id
            WHERE (:tipo IS NULL OR m.tipo::text = :tipo)
            AND (:inicio IS NULL OR m.created_at >= CAST(:inicio AS TIMESTAMP))
            AND (:fin IS NULL OR m.created_at <= CAST(:fin AS TIMESTAMP))
            ORDER BY m.created_at DESC
            """;

            return h.createQuery(query)
                    .bind("tipo", (tipo == null || tipo.isEmpty()) ? null : tipo)
                    .bind("inicio", (fechaInicio == null || fechaInicio.isEmpty()) ? null : fechaInicio)
                    .bind("fin", (fechaFin == null || fechaFin.isEmpty()) ? null : fechaFin)
                    .mapToMap()
                    .list();
        });
    }

    public Map<String, Object> obtenerTotales() {
        return jdbi.withHandle(h -> h.createQuery("""
        SELECT 
            COALESCE(SUM(CASE WHEN tipo::text = 'INGRESO' THEN monto ELSE 0 END), 0) as ingresos,
            COALESCE(SUM(CASE WHEN tipo::text = 'EGRESO' THEN monto ELSE 0 END), 0) as egresos
        FROM movimientos
    """).mapToMap().one());
    }

    public void reversar(Long movimientoId, String motivo) {
        jdbi.useTransaction(handle -> {
            var mov = handle.createQuery("SELECT * FROM movimientos WHERE id = :id")
                    .bind("id", movimientoId).mapToMap().one();

            BigDecimal monto = (BigDecimal) mov.get("monto");
            Long obId = ((Number) mov.get("obligacion_id")).longValue();
            String tipoOriginal = mov.get("tipo").toString();

            String tipoReversa = tipoOriginal.equals("INGRESO") ? "EGRESO" : "INGRESO";

            handle.createUpdate("""
                INSERT INTO movimientos (obligacion_id, tipo, monto, descripcion) 
                VALUES (:obId, :tipo::tipo_movimiento_enum, :monto, :desc)
            """)
                    .bind("obId", obId)
                    .bind("tipo", tipoReversa)
                    .bind("monto", monto)
                    .bind("desc", "REVERSO: " + motivo)
                    .execute();

            String operador = tipoReversa.equals("INGRESO") ? "-" : "+";

            handle.createUpdate("UPDATE obligaciones SET saldo = saldo " + operador + " :monto, estado = 'PENDIENTE' WHERE id = :id")
                    .bind("monto", monto)
                    .bind("id", obId)
                    .execute();
        });
    }
    public void registrarIngresoDirectoCompleto(Map<String, Object> datos) {
        jdbi.useTransaction(handle -> {
            // 1. CREAR EL CONCEPTO
            // Se asume que campos 'nombre' vienen del modal
            Long conceptoId = handle.createUpdate("""
                INSERT INTO conceptos (nombre, descripcion, activo)
                VALUES (:nom, :desc, true)
            """)
                    .bind("nom", datos.get("concepto_nombre"))
                    .bind("desc", "Ingreso directo - " + datos.get("concepto_nombre"))
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one();

            // 2. VINCULAR CONCEPTO CON EL TIPO DE TERCERO (Validación)
            handle.createUpdate("""
                INSERT INTO validacion_concepto (concepto_id, tipo_tercero)
                VALUES (:cid, :tt::tipo_tercero_enum)
            """)
                    .bind("cid", conceptoId)
                    .bind("tt", datos.get("tipo_tercero"))
                    .execute();

            // 3. CREAR LA OBLIGACIÓN (Saldada automáticamente)
            Long obId = handle.createUpdate("""
                INSERT INTO obligaciones (concepto_id, tipo_tercero, tercero_id, monto_total, saldo, descripcion, estado)
                VALUES (:cid, :tt::tipo_tercero_enum, :tid, :m, 0, :d, 'PAGADO')
            """)
                    .bind("cid", conceptoId)
                    .bind("tt", datos.get("tipo_tercero"))
                    .bind("tid", datos.get("tercero_id"))
                    .bind("m", datos.get("monto"))
                    .bind("d", datos.get("descripcion"))
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one();

            // 4. CREAR EL MOVIMIENTO (Ingreso de dinero)
            handle.createUpdate("""
                INSERT INTO movimientos (obligacion_id, tipo, monto, descripcion)
                VALUES (:oid, 'INGRESO'::tipo_movimiento_enum, :m, :d)
            """)
                    .bind("oid", obId)
                    .bind("m", datos.get("monto"))
                    .bind("d", "PAGO DIRECTO: " + datos.get("descripcion"))
                    .execute();
        });
    }
}