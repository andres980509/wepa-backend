package ETI.sgc.document;

import ETI.sgc.http.Pagination;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class DocumentDAO {
    private final Jdbi jdbi;

    public DocumentDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public int nextVersion(String entidadTipo, Long entidadId, String tipoDocumento, String folderPath) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT COALESCE(MAX(version), 0) + 1
                FROM documentos
                WHERE entidad_tipo = :entidadTipo::entidad_tipo_enum
                  AND entidad_id = :entidadId
                  AND tipo_documento = :tipoDocumento::tipo_documento_enum
                  AND COALESCE(folder_path, '') = COALESCE(:folderPath, '')
                """)
                .bind("entidadTipo", entidadTipo)
                .bind("entidadId", entidadId)
                .bind("tipoDocumento", tipoDocumento)
                .bind("folderPath", folderPath)
                .mapTo(Integer.class)
                .one());
    }

    public <T> T withVersionLock(
            String entidadTipo,
            Long entidadId,
            String tipoDocumento,
            String folderPath,
            BiFunction<Handle, Integer, T> callback
    ) {
        return jdbi.inTransaction(handle -> {
            String lockKey = entidadTipo + ":" + entidadId + ":" + tipoDocumento + ":" + (folderPath == null ? "" : folderPath);
            handle.createQuery("SELECT pg_advisory_xact_lock(hashtext(:lockKey))")
                    .bind("lockKey", lockKey)
                    .mapToMap()
                    .one();

            int version = handle.createQuery("""
                    SELECT COALESCE(MAX(version), 0) + 1
                    FROM documentos
                    WHERE entidad_tipo = :entidadTipo::entidad_tipo_enum
                      AND entidad_id = :entidadId
                      AND tipo_documento = :tipoDocumento::tipo_documento_enum
                      AND COALESCE(folder_path, '') = COALESCE(:folderPath, '')
                    """)
                    .bind("entidadTipo", entidadTipo)
                    .bind("entidadId", entidadId)
                    .bind("tipoDocumento", tipoDocumento)
                    .bind("folderPath", folderPath)
                    .mapTo(Integer.class)
                    .one();

            return callback.apply(handle, version);
        });
    }

    public Long insert(
            String nombreArchivo,
            String rutaUrl,
            String tipoDocumento,
            String entidadTipo,
            Long entidadId,
            String folderPath,
            String originalFilename,
            String mimeType,
            long fileSizeBytes,
            String checksumSha256,
            int version,
            String uploadedBy
    ) {
        return jdbi.withHandle(handle -> insert(handle,
                nombreArchivo,
                rutaUrl,
                tipoDocumento,
                entidadTipo,
                entidadId,
                folderPath,
                originalFilename,
                mimeType,
                fileSizeBytes,
                checksumSha256,
                version,
                uploadedBy,
                null,
                null
        ));
    }

    public Long insert(
            Handle handle,
            String nombreArchivo,
            String rutaUrl,
            String tipoDocumento,
            String entidadTipo,
            Long entidadId,
            String folderPath,
            String originalFilename,
            String mimeType,
            long fileSizeBytes,
            String checksumSha256,
            int version,
            String uploadedBy,
            String fechaVencimiento,
            String observaciones
    ) {
        return handle.createUpdate("""
                INSERT INTO documentos (
                    nombre_archivo, ruta_url, tipo_documento, entidad_tipo, entidad_id,
                    folder_path, original_filename, mime_type, file_size_bytes,
                    checksum_sha256, version, uploaded_by, fecha_vencimiento,
                    observaciones, estado, created_at
                )
                VALUES (
                    :nombreArchivo, :rutaUrl, :tipoDocumento::tipo_documento_enum,
                    :entidadTipo::entidad_tipo_enum, :entidadId, :folderPath,
                    :originalFilename, :mimeType, :fileSizeBytes, :checksumSha256,
                    :version, :uploadedBy, CAST(:fechaVencimiento AS date),
                    :observaciones, 'PENDIENTE', NOW()
                )
                """)
                .bind("nombreArchivo", nombreArchivo)
                .bind("rutaUrl", rutaUrl)
                .bind("tipoDocumento", tipoDocumento)
                .bind("entidadTipo", entidadTipo)
                .bind("entidadId", entidadId)
                .bind("folderPath", folderPath)
                .bind("originalFilename", originalFilename)
                .bind("mimeType", mimeType)
                .bind("fileSizeBytes", fileSizeBytes)
                .bind("checksumSha256", checksumSha256)
                .bind("version", version)
                .bind("uploadedBy", uploadedBy)
                .bind("fechaVencimiento", fechaVencimiento)
                .bind("observaciones", observaciones)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one();
    }

    public Map<String, Object> findById(Long id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT *
                FROM documentos
                WHERE id = :id
                  AND COALESCE(active, true) = true
                  AND deleted_at IS NULL
                """)
                .bind("id", id)
                .mapToMap()
                .findOne()
                .orElse(null));
    }

    public List<Map<String, Object>> listByEntity(String entidadTipo, Long entidadId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT *
                FROM documentos
                WHERE entidad_tipo = :entidadTipo::entidad_tipo_enum
                  AND entidad_id = :entidadId
                  AND COALESCE(active, true) = true
                ORDER BY folder_path ASC NULLS FIRST, tipo_documento ASC, version DESC, created_at DESC
                """)
                .bind("entidadTipo", entidadTipo)
                .bind("entidadId", entidadId)
                .mapToMap()
                .list());
    }

    public List<Map<String, Object>> search(DocumentSearchFilters filters, Pagination pagination) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                WITH enriched AS (
                    SELECT d.*,
                           COALESCE(
                               (SELECT i.nombre_completo FROM integrantes i WHERE i.id = d.entidad_id AND d.entidad_tipo::text = 'INTEGRANTE'),
                               (SELECT u.username FROM usuarios u WHERE u.id = d.entidad_id AND d.entidad_tipo::text = 'USUARIO'),
                               (SELECT COALESCE(p.razon_social, p.nombre) FROM proveedores p WHERE p.id = d.entidad_id AND d.entidad_tipo::text = 'PROVEEDOR'),
                               (SELECT pat.nombre FROM patrocinadores pat WHERE pat.id = d.entidad_id AND d.entidad_tipo::text = 'PATROCINADOR'),
                               (SELECT e.nombre FROM entidades e WHERE e.id = d.entidad_id AND d.entidad_tipo::text = 'ENTIDAD'),
                               (SELECT CONCAT(c.nombre, ' - ', COALESCE(o.descripcion, ''))
                                FROM obligaciones o
                                JOIN conceptos c ON c.id = o.concepto_id
                                WHERE o.id = d.entidad_id AND d.entidad_tipo::text = 'OBLIGACION'),
                               (SELECT m.descripcion FROM movimientos m WHERE m.id = d.entidad_id AND d.entidad_tipo::text IN ('MOVIMIENTO', 'MOVIMIENTO_FINANCIERO')),
                               CONCAT(d.entidad_tipo::text, ' #', d.entidad_id)
                           ) AS entidad_nombre,
                           COALESCE(
                               (SELECT CONCAT_WS(' · ', NULLIF(CONCAT_WS(' ', i.tipo_documento, i.numero_documento), ''), NULLIF(i.correo, ''))
                                FROM integrantes i WHERE i.id = d.entidad_id AND d.entidad_tipo::text = 'INTEGRANTE'),
                               (SELECT CONCAT_WS(' · ', u.rol, CASE WHEN u.activo THEN 'Activo' ELSE 'Inactivo' END)
                                FROM usuarios u WHERE u.id = d.entidad_id AND d.entidad_tipo::text = 'USUARIO'),
                               (SELECT CONCAT_WS(' · ', p.tipo, p.nit, p.correo)
                                FROM proveedores p WHERE p.id = d.entidad_id AND d.entidad_tipo::text = 'PROVEEDOR'),
                               (SELECT CONCAT_WS(' · ', pat.tipo, pat.empresa, pat.correo)
                                FROM patrocinadores pat WHERE pat.id = d.entidad_id AND d.entidad_tipo::text = 'PATROCINADOR'),
                               (SELECT CONCAT_WS(' · ', e.tipo, e.nit, e.correo)
                                FROM entidades e WHERE e.id = d.entidad_id AND d.entidad_tipo::text = 'ENTIDAD'),
                               (SELECT CONCAT_WS(' · ', o.tipo_tercero::text, 'Saldo ' || o.saldo)
                                FROM obligaciones o WHERE o.id = d.entidad_id AND d.entidad_tipo::text = 'OBLIGACION'),
                               (SELECT CONCAT_WS(' · ', m.tipo::text, 'Valor ' || m.monto, TO_CHAR(m.created_at, 'YYYY-MM-DD'))
                                FROM movimientos m WHERE m.id = d.entidad_id AND d.entidad_tipo::text IN ('MOVIMIENTO', 'MOVIMIENTO_FINANCIERO')),
                               ''
                           ) AS entidad_detalle
                    FROM documentos d
                    WHERE COALESCE(d.active, true) = true
                      AND d.deleted_at IS NULL
                )
                SELECT *
                FROM enriched
                WHERE 1 = 1
                  AND (:entidadTipo IS NULL OR entidad_tipo::text = :entidadTipo)
                  AND (:entidadId IS NULL OR entidad_id = :entidadId)
                  AND (:tipoDocumento IS NULL OR tipo_documento::text = :tipoDocumento)
                  AND (:estado IS NULL OR estado = :estado)
                  AND (:folderPath IS NULL OR COALESCE(folder_path, '') ILIKE :folderPath)
                  AND (
                    :nombre IS NULL
                    OR original_filename ILIKE :nombre
                    OR nombre_archivo ILIKE :nombre
                    OR entidad_nombre ILIKE :nombre
                    OR entidad_detalle ILIKE :nombre
                    OR uploaded_by ILIKE :nombre
                    OR validado_por ILIKE :nombre
                  )
                  AND (:fechaDesde IS NULL OR created_at >= CAST(:fechaDesde AS timestamptz))
                  AND (:fechaHasta IS NULL OR created_at <= CAST(:fechaHasta AS timestamptz))
                  AND (
                    :vencimiento IS NULL
                    OR (:vencimiento = 'VENCIDOS' AND fecha_vencimiento < CURRENT_DATE)
                    OR (:vencimiento = 'PROXIMOS' AND fecha_vencimiento BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days')
                    OR (:vencimiento = 'SIN_VENCIMIENTO' AND fecha_vencimiento IS NULL)
                  )
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """)
                .bind("entidadTipo", blankToNull(filters.entidadTipo))
                .bind("entidadId", filters.entidadId)
                .bind("tipoDocumento", blankToNull(filters.tipoDocumento))
                .bind("estado", blankToNull(filters.estado))
                .bind("fechaDesde", blankToNull(filters.fechaDesde))
                .bind("fechaHasta", blankToNull(filters.fechaHasta))
                .bind("vencimiento", blankToNull(filters.vencimiento))
                .bind("folderPath", like(filters.folderPath))
                .bind("nombre", like(filters.nombre))
                .bind("limit", pagination.size)
                .bind("offset", pagination.offset)
                .mapToMap()
                .list());
    }

    public long count(DocumentSearchFilters filters) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                WITH enriched AS (
                    SELECT d.*,
                           COALESCE(
                               (SELECT i.nombre_completo FROM integrantes i WHERE i.id = d.entidad_id AND d.entidad_tipo::text = 'INTEGRANTE'),
                               (SELECT u.username FROM usuarios u WHERE u.id = d.entidad_id AND d.entidad_tipo::text = 'USUARIO'),
                               (SELECT COALESCE(p.razon_social, p.nombre) FROM proveedores p WHERE p.id = d.entidad_id AND d.entidad_tipo::text = 'PROVEEDOR'),
                               (SELECT pat.nombre FROM patrocinadores pat WHERE pat.id = d.entidad_id AND d.entidad_tipo::text = 'PATROCINADOR'),
                               (SELECT e.nombre FROM entidades e WHERE e.id = d.entidad_id AND d.entidad_tipo::text = 'ENTIDAD'),
                               (SELECT CONCAT(c.nombre, ' - ', COALESCE(o.descripcion, ''))
                                FROM obligaciones o
                                JOIN conceptos c ON c.id = o.concepto_id
                                WHERE o.id = d.entidad_id AND d.entidad_tipo::text = 'OBLIGACION'),
                               (SELECT m.descripcion FROM movimientos m WHERE m.id = d.entidad_id AND d.entidad_tipo::text IN ('MOVIMIENTO', 'MOVIMIENTO_FINANCIERO')),
                               CONCAT(d.entidad_tipo::text, ' #', d.entidad_id)
                           ) AS entidad_nombre,
                           COALESCE(
                               (SELECT CONCAT_WS(' · ', NULLIF(CONCAT_WS(' ', i.tipo_documento, i.numero_documento), ''), NULLIF(i.correo, ''))
                                FROM integrantes i WHERE i.id = d.entidad_id AND d.entidad_tipo::text = 'INTEGRANTE'),
                               (SELECT CONCAT_WS(' · ', u.rol, CASE WHEN u.activo THEN 'Activo' ELSE 'Inactivo' END)
                                FROM usuarios u WHERE u.id = d.entidad_id AND d.entidad_tipo::text = 'USUARIO'),
                               (SELECT CONCAT_WS(' · ', p.tipo, p.nit, p.correo)
                                FROM proveedores p WHERE p.id = d.entidad_id AND d.entidad_tipo::text = 'PROVEEDOR'),
                               (SELECT CONCAT_WS(' · ', pat.tipo, pat.empresa, pat.correo)
                                FROM patrocinadores pat WHERE pat.id = d.entidad_id AND d.entidad_tipo::text = 'PATROCINADOR'),
                               (SELECT CONCAT_WS(' · ', e.tipo, e.nit, e.correo)
                                FROM entidades e WHERE e.id = d.entidad_id AND d.entidad_tipo::text = 'ENTIDAD'),
                               (SELECT CONCAT_WS(' · ', o.tipo_tercero::text, 'Saldo ' || o.saldo)
                                FROM obligaciones o WHERE o.id = d.entidad_id AND d.entidad_tipo::text = 'OBLIGACION'),
                               (SELECT CONCAT_WS(' · ', m.tipo::text, 'Valor ' || m.monto, TO_CHAR(m.created_at, 'YYYY-MM-DD'))
                                FROM movimientos m WHERE m.id = d.entidad_id AND d.entidad_tipo::text IN ('MOVIMIENTO', 'MOVIMIENTO_FINANCIERO')),
                               ''
                           ) AS entidad_detalle
                    FROM documentos d
                    WHERE COALESCE(d.active, true) = true
                      AND d.deleted_at IS NULL
                )
                SELECT COUNT(*)
                FROM enriched
                WHERE 1 = 1
                  AND (:entidadTipo IS NULL OR entidad_tipo::text = :entidadTipo)
                  AND (:entidadId IS NULL OR entidad_id = :entidadId)
                  AND (:tipoDocumento IS NULL OR tipo_documento::text = :tipoDocumento)
                  AND (:estado IS NULL OR estado = :estado)
                  AND (:folderPath IS NULL OR COALESCE(folder_path, '') ILIKE :folderPath)
                  AND (
                    :nombre IS NULL
                    OR original_filename ILIKE :nombre
                    OR nombre_archivo ILIKE :nombre
                    OR entidad_nombre ILIKE :nombre
                    OR entidad_detalle ILIKE :nombre
                    OR uploaded_by ILIKE :nombre
                    OR validado_por ILIKE :nombre
                  )
                  AND (:fechaDesde IS NULL OR created_at >= CAST(:fechaDesde AS timestamptz))
                  AND (:fechaHasta IS NULL OR created_at <= CAST(:fechaHasta AS timestamptz))
                  AND (
                    :vencimiento IS NULL
                    OR (:vencimiento = 'VENCIDOS' AND fecha_vencimiento < CURRENT_DATE)
                    OR (:vencimiento = 'PROXIMOS' AND fecha_vencimiento BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days')
                    OR (:vencimiento = 'SIN_VENCIMIENTO' AND fecha_vencimiento IS NULL)
                  )
                """)
                .bind("entidadTipo", blankToNull(filters.entidadTipo))
                .bind("entidadId", filters.entidadId)
                .bind("tipoDocumento", blankToNull(filters.tipoDocumento))
                .bind("estado", blankToNull(filters.estado))
                .bind("fechaDesde", blankToNull(filters.fechaDesde))
                .bind("fechaHasta", blankToNull(filters.fechaHasta))
                .bind("vencimiento", blankToNull(filters.vencimiento))
                .bind("folderPath", like(filters.folderPath))
                .bind("nombre", like(filters.nombre))
                .mapTo(Long.class)
                .one());
    }

    public List<Map<String, Object>> expired(Pagination pagination) {
        DocumentSearchFilters filters = new DocumentSearchFilters();
        filters.vencimiento = "VENCIDOS";
        return search(filters, pagination);
    }

    public List<Map<String, Object>> versions(Long id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                WITH base AS (
                    SELECT entidad_tipo, entidad_id, tipo_documento, COALESCE(folder_path, '') AS folder_path
                    FROM documentos
                    WHERE id = :id
                )
                SELECT d.*
                FROM documentos d
                JOIN base b ON d.entidad_tipo = b.entidad_tipo
                    AND d.entidad_id = b.entidad_id
                    AND d.tipo_documento = b.tipo_documento
                    AND COALESCE(d.folder_path, '') = b.folder_path
                WHERE COALESCE(d.active, true) = true
                  AND d.deleted_at IS NULL
                ORDER BY d.version DESC, d.created_at DESC
                """)
                .bind("id", id)
                .mapToMap()
                .list());
    }

    public List<Map<String, Object>> listTypes(String entidadTipo) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT *
                FROM document_types
                WHERE activo = true
                  AND (:entidadTipo IS NULL OR entidad_tipo IS NULL OR entidad_tipo = :entidadTipo)
                ORDER BY obligatorio DESC, nombre ASC
                """)
                .bind("entidadTipo", blankToNull(entidadTipo))
                .mapToMap()
                .list());
    }

    public Long createType(Map<String, Object> payload) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                INSERT INTO document_types (
                    codigo, nombre, descripcion, entidad_tipo,
                    obligatorio, vigencia_dias, activo, created_at
                )
                VALUES (
                    :codigo, :nombre, :descripcion, :entidadTipo,
                    :obligatorio, :vigenciaDias, true, NOW()
                )
                ON CONFLICT (codigo) DO UPDATE
                SET nombre = EXCLUDED.nombre,
                    descripcion = EXCLUDED.descripcion,
                    entidad_tipo = EXCLUDED.entidad_tipo,
                    obligatorio = EXCLUDED.obligatorio,
                    vigencia_dias = EXCLUDED.vigencia_dias,
                    activo = true
                RETURNING id
                """)
                .bind("codigo", String.valueOf(payload.get("codigo")).trim().toUpperCase())
                .bind("nombre", payload.get("nombre"))
                .bind("descripcion", payload.get("descripcion"))
                .bind("entidadTipo", payload.get("entidad_tipo"))
                .bind("obligatorio", Boolean.TRUE.equals(payload.get("obligatorio")))
                .bind("vigenciaDias", payload.get("vigencia_dias"))
                .mapTo(Long.class)
                .one());
    }

    public void updateStatus(Long id, String estado, String observaciones, String usuario, boolean validated) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE documentos
                SET estado = :estado,
                    observaciones = COALESCE(:observaciones, observaciones),
                    validado_por = CASE WHEN :validated THEN :usuario ELSE validado_por END,
                    validado_en = CASE WHEN :validated THEN NOW() ELSE validado_en END
                WHERE id = :id
                  AND COALESCE(active, true) = true
                  AND deleted_at IS NULL
                """)
                .bind("id", id)
                .bind("estado", estado)
                .bind("observaciones", observaciones)
                .bind("usuario", usuario)
                .bind("validated", validated)
                .execute());
    }

    public Map<String, Object> dashboard() {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT
                    COUNT(*) FILTER (WHERE COALESCE(active, true) = true AND deleted_at IS NULL) AS total_documentos,
                    COUNT(*) FILTER (WHERE estado = 'PENDIENTE' AND COALESCE(active, true) = true AND deleted_at IS NULL) AS pendientes,
                    COUNT(*) FILTER (WHERE estado = 'VALIDADO' AND COALESCE(active, true) = true AND deleted_at IS NULL) AS validados,
                    COUNT(*) FILTER (WHERE estado = 'RECHAZADO' AND COALESCE(active, true) = true AND deleted_at IS NULL) AS rechazados,
                    COUNT(*) FILTER (WHERE (estado = 'VENCIDO' OR fecha_vencimiento < CURRENT_DATE) AND COALESCE(active, true) = true AND deleted_at IS NULL) AS vencidos,
                    COUNT(*) FILTER (WHERE fecha_vencimiento BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days' AND COALESCE(active, true) = true AND deleted_at IS NULL) AS proximos_vencer
                FROM documentos
                """)
                .mapToMap()
                .one());
    }

    public List<Map<String, Object>> missing(String entidadTipo, Long entidadId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT dt.*
                FROM document_types dt
                WHERE dt.activo = true
                  AND dt.obligatorio = true
                  AND (:entidadTipo IS NULL OR dt.entidad_tipo IS NULL OR dt.entidad_tipo = :entidadTipo)
                  AND (
                    :entidadId IS NULL
                    OR NOT EXISTS (
                        SELECT 1
                        FROM documentos d
                        WHERE d.entidad_tipo::text = :entidadTipo
                          AND d.entidad_id = :entidadId
                          AND d.tipo_documento::text = dt.codigo
                          AND d.estado IN ('PENDIENTE', 'VALIDADO')
                          AND COALESCE(d.active, true) = true
                          AND d.deleted_at IS NULL
                    )
                  )
                ORDER BY dt.nombre ASC
                """)
                .bind("entidadTipo", blankToNull(entidadTipo))
                .bind("entidadId", entidadId)
                .mapToMap()
                .list());
    }

    private String like(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : "%" + normalized + "%";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
