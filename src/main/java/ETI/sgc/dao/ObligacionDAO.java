package ETI.sgc.dao;

import ETI.sgc.dto.RegistroPagoDTO;
import ETI.sgc.model.Obligacion;
import org.jdbi.v3.core.Jdbi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class ObligacionDAO {
    private final Jdbi jdbi;

    public ObligacionDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Long crear(Long conceptoId, String tipo, Long terceroId, BigDecimal monto, String descripcion) {
        return jdbi.withHandle(h ->
                h.createUpdate("""
                INSERT INTO obligaciones
                (concepto_id, tipo_tercero, tercero_id, monto_total, saldo, estado, descripcion)
                /* Se agrega el cast ::tipo_tercero_enum para evitar el error de tipos en Postgres */
                VALUES (:c, :t::tipo_tercero_enum, :tercero, :monto, :monto, 'PENDIENTE', :desc)
            """)
                        .bind("c", conceptoId)
                        .bind("t", tipo)
                        .bind("tercero", terceroId)
                        .bind("monto", monto)
                        .bind("desc", descripcion)
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Long.class)
                        .one()
        );
    }

    public Long crear(org.jdbi.v3.core.Handle handle, Long conceptoId, String tipo, Long terceroId, BigDecimal monto, String descripcion) {
        return handle.createUpdate("""
                INSERT INTO obligaciones
                (concepto_id, tipo_tercero, tercero_id, monto_total, saldo, estado, descripcion)
                VALUES (:c, :t::tipo_tercero_enum, :tercero, :monto, :monto, 'PENDIENTE', :desc)
            """)
                .bind("c", conceptoId)
                .bind("t", tipo)
                .bind("tercero", terceroId)
                .bind("monto", monto)
                .bind("desc", descripcion)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one();
    }

    public Map<String, Object> obtenerResumenDashboard() {
        return jdbi.withHandle(h -> h.createQuery("""
        SELECT 
            -- 1. Deuda que los integrantes tienen conmigo (Activos)
            (SELECT COUNT(*) FROM obligaciones WHERE tipo_tercero = 'INTEGRANTE' AND estado = 'PENDIENTE') as integrantes_pendientes_count,
            (SELECT COALESCE(SUM(saldo), 0) FROM obligaciones WHERE tipo_tercero = 'INTEGRANTE' AND estado = 'PENDIENTE') as total_deuda_integrantes,
            
            -- 2. Deuda que yo tengo con terceros (Pasivos/Cuentas por pagar)
            (SELECT COALESCE(SUM(saldo), 0) FROM obligaciones WHERE tipo_tercero != 'INTEGRANTE' AND estado = 'PENDIENTE') as cuentas_por_pagar_terceros,
            
            -- 3. Total histórico de gastos (para el balance neto)
            (SELECT COALESCE(SUM(monto_total), 0) FROM obligaciones WHERE tipo_tercero != 'INTEGRANTE') as total_gastos_historico,
            
            -- 4. Recaudado Real (Lo que ha entrado a caja)
            (SELECT COALESCE(SUM(monto), 0) FROM movimientos WHERE tipo = 'INGRESO') as total_recaudado,
            
            -- 5. Salida Real (Lo que ha salido de caja)
            (SELECT COALESCE(SUM(monto), 0) FROM movimientos WHERE tipo = 'EGRESO') as total_egresado
    """).mapToMap().one());
    }

    public List<Map<String, Object>> buscarObligacionesTerceros(String query) {
        return jdbi.withHandle(h -> h.createQuery("""
        SELECT 
            o.*, 
            c.nombre as concepto,
            -- Buscamos el nombre dinámicamente según el tipo de tercero
            COALESCE(
                (SELECT p.nombre FROM proveedores p WHERE p.id = o.tercero_id AND o.tipo_tercero::text = 'PROVEEDOR'),
                (SELECT pat.nombre FROM patrocinadores pat WHERE pat.id = o.tercero_id AND o.tipo_tercero::text = 'PATROCINADOR'),
                (SELECT ent.nombre FROM entidades ent WHERE ent.id = o.tercero_id AND o.tipo_tercero::text = 'ENTIDAD'),
                o.descripcion, 
                c.nombre
            ) as tercero_nombre
        FROM obligaciones o
        JOIN conceptos c ON o.concepto_id = c.id
        WHERE o.tipo_tercero::text != 'INTEGRANTE'
        AND o.estado = 'PENDIENTE' 
        AND o.saldo > 0
        AND (
            o.descripcion ILIKE :q 
            OR c.nombre ILIKE :q
            -- Búsqueda en tablas relacionadas
            OR EXISTS (SELECT 1 FROM proveedores p WHERE p.id = o.tercero_id AND p.nombre ILIKE :q)
            OR EXISTS (SELECT 1 FROM entidades e WHERE e.id = o.tercero_id AND e.nombre ILIKE :q)
        )
        ORDER BY o.created_at DESC
    """)
                .bind("q", "%" + (query == null ? "" : query) + "%")
                .mapToMap().list());
    }
    public List<Map<String, Object>> buscarDeudores(String query) {
        return jdbi.withHandle(h -> h.createQuery("""
        SELECT 
            o.*, 
            c.nombre as concepto, 
            i.nombre_completo as tercero_nombre,
            i.tipo_documento,
            i.numero_documento,
            i.correo
        FROM obligaciones o
        JOIN conceptos c ON o.concepto_id = c.id
        INNER JOIN integrantes i ON o.tercero_id = i.id
        WHERE o.estado = 'PENDIENTE' 
        AND o.tipo_tercero = 'INTEGRANTE'
        AND (
            i.nombre_completo ILIKE :q 
            OR c.nombre ILIKE :q
        )
        ORDER BY o.saldo DESC
        -- LIMIT ELIMINADO: Mandamos todo para paginar en Vue
    """)
                .bind("q", "%" + query + "%")
                .mapToMap().list());
    }

    public List<Map<String, Object>> buscarObligacionesIntegrantes(String query) {
        return jdbi.withHandle(h -> h.createQuery("""
        SELECT
            o.id,
            o.concepto_id,
            o.tercero_id,
            o.tipo_tercero,
            o.monto_total,
            o.saldo,
            o.estado,
            o.descripcion,
            o.created_at,
            c.nombre AS concepto,
            c.nombre AS concepto_nombre,
            i.id AS integrante_id,
            i.nombre_completo AS tercero_nombre,
            i.tipo_documento,
            i.numero_documento,
            i.correo,
            (o.monto_total - o.saldo) AS pagado,
            COALESCE(tx.total_transacciones, 0) AS total_transacciones,
            COALESCE(tx.pendientes, 0) AS transacciones_pendientes,
            COALESCE(tx.aprobadas, 0) AS transacciones_aprobadas,
            COALESCE(tx.rechazadas, 0) AS transacciones_rechazadas,
            COALESCE(tx.expiradas, 0) AS transacciones_expiradas,
            latest.status AS latest_payment_status,
            latest.provider AS latest_payment_provider,
            latest.amount AS latest_payment_amount,
            latest.provider_reference AS latest_provider_reference,
            latest.created_at AS latest_payment_created_at,
            latest.updated_at AS latest_payment_updated_at
        FROM obligaciones o
        JOIN conceptos c ON o.concepto_id = c.id
        INNER JOIN integrantes i ON o.tercero_id = i.id
        LEFT JOIN LATERAL (
            SELECT
                COUNT(*) AS total_transacciones,
                COUNT(*) FILTER (WHERE status = 'PENDIENTE') AS pendientes,
                COUNT(*) FILTER (WHERE status = 'APROBADO') AS aprobadas,
                COUNT(*) FILTER (WHERE status = 'RECHAZADO') AS rechazadas,
                COUNT(*) FILTER (WHERE status = 'EXPIRADO') AS expiradas
            FROM payment_transactions pt
            WHERE pt.obligacion_id = o.id
        ) tx ON true
        LEFT JOIN LATERAL (
            SELECT status, provider, amount, provider_reference, created_at, updated_at
            FROM payment_transactions pt
            WHERE pt.obligacion_id = o.id
            ORDER BY pt.created_at DESC
            LIMIT 1
        ) latest ON true
        WHERE o.tipo_tercero = 'INTEGRANTE'
          AND (
            i.nombre_completo ILIKE :q
            OR i.numero_documento ILIKE :q
            OR i.correo ILIKE :q
            OR c.nombre ILIKE :q
            OR o.descripcion ILIKE :q
            OR o.estado::text ILIKE :q
          )
        ORDER BY
            CASE WHEN o.estado = 'PENDIENTE' THEN 0 ELSE 1 END,
            latest.created_at DESC NULLS LAST,
            o.created_at DESC
        LIMIT 120
    """)
                .bind("q", "%" + (query == null ? "" : query) + "%")
                .mapToMap().list());
    }

    public List<Map<String, Object>> listarPorIntegrante(Long integranteId) {
        return jdbi.withHandle(h -> h.createQuery("""
    SELECT 
        o.id,
        o.concepto_id,
        o.tercero_id,
        o.tipo_tercero,
        o.monto_total,
        o.saldo,
        o.estado,
        o.descripcion,
        o.created_at,
        c.nombre as concepto_nombre,
        (o.monto_total - o.saldo) as pagado,
        COALESCE(
            (SELECT json_agg(json_build_object(
                'id', m.id,
                'monto', m.monto,
                'descripcion', m.descripcion,
                'created_at', m.created_at,
                'documentos', COALESCE(
                    (SELECT json_agg(json_build_object(
                        'id', d.id,
                        'nombre', d.nombre_archivo,
                        'ruta_url', d.ruta_url,
                        'tipo_documento', d.tipo_documento
                    ))
                     FROM documentos d
                     WHERE d.entidad_id = m.id AND d.entidad_tipo = 'MOVIMIENTO'
                    ), '[]'::json
                )
            ))
             FROM movimientos m
             WHERE m.obligacion_id = o.id
            ), '[]'::json
        )::text as abonos -- <--- Casteo vital para que JDBI lo entregue como String JSON legible en Vue
    FROM obligaciones o
    JOIN conceptos c ON o.concepto_id = c.id
    WHERE o.tercero_id = :id AND o.tipo_tercero = 'INTEGRANTE'
    ORDER BY o.created_at DESC
""")
                .bind("id", integranteId)
                .mapToMap()
                .list());
    }
    public List<Obligacion> listarPorTercero(Long terceroId, String tipoTercero) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM obligaciones 
                WHERE tercero_id = :terceroId 
                AND tipo_tercero = :tipo::tipo_tercero_enum 
                AND estado = 'PENDIENTE' 
                AND saldo > 0
                ORDER BY id DESC
            """)
                        .bind("terceroId", terceroId)
                        .bind("tipo", tipoTercero)
                        .mapToBean(Obligacion.class)
                        .list()
        );
    }
    public List<Obligacion> listar() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM obligaciones ORDER BY id DESC")
                        .mapToBean(Obligacion.class)
                        .list()
        );
    }

    public Obligacion obtenerConBloqueo(Long id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM obligaciones WHERE id = :id FOR UPDATE")
                        .bind("id", id)
                        .mapToBean(Obligacion.class)
                        .findOne()
                        .orElse(null)
        );
    }

    public Map<String, Object> obtenerDatosRecibo(Long obligacionId) {
        return jdbi.withHandle(h -> h.createQuery("""
                SELECT
                    o.id AS obligacion_id,
                    o.descripcion AS obligacion_descripcion,
                    o.tipo_tercero,
                    o.tercero_id,
                    c.nombre AS concepto_nombre,
                    COALESCE(i.nombre_completo, p.razon_social, p.nombre, pat.nombre, e.nombre, o.descripcion, c.nombre) AS tercero_nombre,
                    COALESCE(i.tipo_documento, 'NIT') AS tipo_documento,
                    COALESCE(i.numero_documento, p.nit, e.nit, '') AS numero_documento,
                    COALESCE(i.telefono, p.telefono, pat.telefono, e.telefono, '') AS telefono,
                    COALESCE(i.correo, p.correo, pat.correo, e.correo, '') AS correo
                FROM obligaciones o
                JOIN conceptos c ON c.id = o.concepto_id
                LEFT JOIN integrantes i ON o.tipo_tercero = 'INTEGRANTE' AND i.id = o.tercero_id
                LEFT JOIN proveedores p ON o.tipo_tercero = 'PROVEEDOR' AND p.id = o.tercero_id
                LEFT JOIN patrocinadores pat ON o.tipo_tercero = 'PATROCINADOR' AND pat.id = o.tercero_id
                LEFT JOIN entidades e ON o.tipo_tercero = 'ENTIDAD' AND e.id = o.tercero_id
                WHERE o.id = :id
                """)
                .bind("id", obligacionId)
                .mapToMap()
                .findOne()
                .orElse(Map.of()));
    }


    public Long registrarPagoCompletoConDocumentos(RegistroPagoDTO datos) {
        return jdbi.inTransaction(handle -> {

            // 1. Bloqueo de fila
            Obligacion ob = handle.createQuery("SELECT * FROM obligaciones WHERE id = :id FOR UPDATE")
                    .bind("id", datos.getObligacionId()) // <-- .get...
                    .mapToBean(Obligacion.class)
                    .findOne()
                    .orElseThrow(() -> new RuntimeException("Obligación no encontrada"));

            if ("PAGADO".equalsIgnoreCase(ob.getEstado())) {
                throw new RuntimeException("La obligación ya está saldada.");
            }

            // 2. Insertar Movimiento
            Long movimientoId = handle.createUpdate("""
                    INSERT INTO movimientos (obligacion_id, tipo, monto, descripcion)
                    VALUES (:o, :t::tipo_movimiento_enum, :m, :d)
                """)
                    .bind("o", datos.getObligacionId())
                    .bind("t", datos.getTipo())
                    .bind("m", datos.getMonto())
                    .bind("d", datos.getDescripcion())
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one();

            // 3. Actualizar Saldo
            handle.createUpdate("""
                    UPDATE obligaciones
                    SET saldo = GREATEST(0, saldo - :monto),
                        estado = CASE 
                                    WHEN (saldo - :monto) <= 0 THEN 'PAGADO'
                                    ELSE 'PENDIENTE'
                                 END
                    WHERE id = :id AND estado != 'PAGADO'
                """)
                    .bind("monto", datos.getMonto())
                    .bind("id", datos.getObligacionId())
                    .execute();

            // 4. Insertar Soporte Externo Adjunto
            if (datos.getComprobanteRutaUrl() != null) {
                handle.createUpdate("""
                        INSERT INTO documentos (
                            nombre_archivo, ruta_url, tipo_documento, entidad_tipo, entidad_id,
                            original_filename, folder_path, estado, validado_por,
                            validado_en, observaciones, uploaded_by, created_at
                        )
                        VALUES (
                            :nombre, :ruta, 'SOPORTE_EXTERNO', 'MOVIMIENTO', :movimiento_id,
                            :nombre, 'pagos', 'VALIDADO', 'SISTEMA',
                            NOW(), 'Soporte validado automaticamente al registrar movimiento aplicado',
                            :uploaded_by, NOW()
                        )
                    """)
                        .bind("nombre", datos.getComprobanteNombre())
                        .bind("ruta", datos.getComprobanteRutaUrl())
                        .bind("movimiento_id", movimientoId)
                        .bind("uploaded_by", datos.getUploadedBy())
                        .execute();
            }

            // 5. Insertar el registro del PDF Oficial
            handle.createUpdate("""
                    INSERT INTO documentos (
                        nombre_archivo, ruta_url, tipo_documento, entidad_tipo, entidad_id,
                        original_filename, mime_type, folder_path, estado, validado_por,
                        validado_en, observaciones, uploaded_by, created_at
                    )
                    VALUES (
                        :nombre, :ruta, 'RECIBO_SISTEMA', 'MOVIMIENTO', :movimiento_id,
                        :nombre, 'application/pdf', 'recibos-sistema', 'VALIDADO', 'SISTEMA',
                        NOW(), 'Recibo generado y validado automaticamente por WEPA',
                        :uploaded_by, NOW()
                    )
                """)
                    .bind("nombre", datos.getReciboSistemaNombre())
                    .bind("ruta", datos.getReciboSistemaRutaUrl())
                    .bind("movimiento_id", movimientoId)
                    .bind("uploaded_by", datos.getUploadedBy())
                    .execute();

            return movimientoId;
        });
    }


    public void registrarPagoAtomico(Long id, BigDecimal monto) {
        jdbi.useHandle(h -> {
            h.createUpdate("""
            UPDATE obligaciones
            SET saldo = GREATEST(0, saldo - :monto),
                estado = CASE 
                            WHEN (saldo - :monto) <= 0 THEN 'PAGADO'
                            ELSE 'PENDIENTE'
                         END
            WHERE id = :id AND estado != 'PAGADO'
        """)
                    .bind("monto", monto)
                    .bind("id", id)
                    .execute();
        });
    }

    public Long registrarPagoPasarela(Long obligacionId, BigDecimal monto, String descripcion, String referenciaPasarela) {
        return jdbi.inTransaction(handle -> {
            Obligacion ob = handle.createQuery("SELECT * FROM obligaciones WHERE id = :id FOR UPDATE")
                    .bind("id", obligacionId)
                    .mapToBean(Obligacion.class)
                    .findOne()
                    .orElseThrow(() -> new RuntimeException("Obligacion no encontrada"));

            if ("PAGADO".equalsIgnoreCase(ob.getEstado())) {
                throw new RuntimeException("La obligacion ya esta saldada.");
            }

            BigDecimal montoAplicado = monto.min(ob.getSaldo());
            Long movimientoId = handle.createUpdate("""
                    INSERT INTO movimientos (obligacion_id, tipo, monto, descripcion)
                    VALUES (:o, 'INGRESO'::tipo_movimiento_enum, :m, :d)
                """)
                    .bind("o", obligacionId)
                    .bind("m", montoAplicado)
                    .bind("d", descripcion + " (" + referenciaPasarela + ")")
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one();

            handle.createUpdate("""
                    UPDATE obligaciones
                    SET saldo = GREATEST(0, saldo - :monto),
                        estado = CASE
                                    WHEN (saldo - :monto) <= 0 THEN 'PAGADO'
                                    ELSE 'PENDIENTE'
                                 END
                    WHERE id = :id
                """)
                    .bind("monto", montoAplicado)
                    .bind("id", obligacionId)
                    .execute();

            return movimientoId;
        });
    }

    public void registrarReciboSistemaDocumento(Long movimientoId, String nombre, String ruta, String validadoPor) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO documentos (
                    nombre_archivo, ruta_url, tipo_documento, entidad_tipo, entidad_id,
                    original_filename, mime_type, folder_path, estado, validado_por,
                    validado_en, observaciones, created_at
                )
                SELECT
                    :nombre, :ruta, 'RECIBO_SISTEMA', 'MOVIMIENTO', :movimientoId,
                    :nombre, 'application/pdf', 'recibos-sistema', 'VALIDADO', :validadoPor,
                    NOW(), 'Recibo generado y validado automaticamente por WEPA', NOW()
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM documentos
                    WHERE entidad_tipo = 'MOVIMIENTO'
                      AND entidad_id = :movimientoId
                      AND tipo_documento = 'RECIBO_SISTEMA'
                      AND ruta_url = :ruta
                )
                """)
                .bind("movimientoId", movimientoId)
                .bind("nombre", nombre)
                .bind("ruta", ruta)
                .bind("validadoPor", validadoPor == null || validadoPor.isBlank() ? "SISTEMA" : validadoPor)
                .execute());
    }
}
