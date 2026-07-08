package ETI.sgc.payment.transfer;

import ETI.sgc.error.ApiException;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class PaymentTransferDAO {
    private final Jdbi jdbi;

    public PaymentTransferDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Map<String, Object> findObligationForIntegrante(Long obligationId, Long integranteId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT o.*, c.nombre AS concepto_nombre, i.nombre_completo, i.tipo_documento,
                       i.numero_documento, i.telefono, i.correo
                FROM obligaciones o
                JOIN conceptos c ON c.id = o.concepto_id
                JOIN integrantes i ON i.id = o.tercero_id
                WHERE o.id = :obligationId
                  AND o.tercero_id = :integranteId
                  AND o.tipo_tercero = 'INTEGRANTE'
                """)
                .bind("obligationId", obligationId)
                .bind("integranteId", integranteId)
                .mapToMap()
                .findOne()
                .orElse(null));
    }

    public Map<String, Object> findIntegranteObligation(Long obligationId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT o.*, c.nombre AS concepto_nombre, i.nombre_completo, i.tipo_documento,
                       i.numero_documento, i.telefono, i.correo
                FROM obligaciones o
                JOIN conceptos c ON c.id = o.concepto_id
                JOIN integrantes i ON i.id = o.tercero_id
                WHERE o.id = :obligationId
                  AND o.tipo_tercero = 'INTEGRANTE'
                """)
                .bind("obligationId", obligationId)
                .mapToMap()
                .findOne()
                .orElse(null));
    }

    public Long createPending(
            Long obligacionId,
            Long usuarioId,
            Long integranteId,
            BigDecimal amount,
            String method,
            String bank,
            String reference,
            StoredTransferSupport support
    ) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                INSERT INTO payment_transfers (
                    obligacion_id, usuario_id, integrante_id, amount, method, bank, reference,
                    status, support_path, support_original_filename, support_mime_type,
                    support_size_bytes, submitted_at, created_at, updated_at
                )
                VALUES (
                    :obligacionId, :usuarioId, :integranteId, :amount, :method, :bank, :reference,
                    'PENDING_VERIFICATION', :supportPath, :supportOriginalFilename, :supportMimeType,
                    :supportSizeBytes, NOW(), NOW(), NOW()
                )
                """)
                .bind("obligacionId", obligacionId)
                .bind("usuarioId", usuarioId)
                .bind("integranteId", integranteId)
                .bind("amount", amount)
                .bind("method", method)
                .bind("bank", bank)
                .bind("reference", reference)
                .bind("supportPath", support.path)
                .bind("supportOriginalFilename", support.originalFilename)
                .bind("supportMimeType", support.mimeType)
                .bind("supportSizeBytes", support.sizeBytes)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one());
    }

    public boolean existsActiveReference(Long integranteId, String reference) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT COUNT(*)
                FROM payment_transfers
                WHERE integrante_id = :integranteId
                  AND reference = :reference
                  AND status IN ('PENDING_VERIFICATION', 'APPROVED')
                """)
                .bind("integranteId", integranteId)
                .bind("reference", reference)
                .mapTo(Long.class)
                .one() > 0);
    }

    public void insertEvent(Long transferId, String action, String username, String observations, String fromStatus, String toStatus) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO payment_transfer_events (
                    transfer_id, action, username, observations, from_status, to_status, created_at
                )
                VALUES (:transferId, :action, :username, :observations, :fromStatus, :toStatus, NOW())
                """)
                .bind("transferId", transferId)
                .bind("action", action)
                .bind("username", username)
                .bind("observations", observations)
                .bind("fromStatus", fromStatus)
                .bind("toStatus", toStatus)
                .execute());
    }

    public Map<String, Object> findById(Long id) {
        return jdbi.withHandle(handle -> richQuery(handle.createQuery("""
                SELECT pt.*, u.username, i.nombre_completo, i.tipo_documento, i.numero_documento,
                       i.telefono, i.correo, o.descripcion AS obligacion_descripcion,
                       o.estado AS obligacion_estado, o.saldo AS obligacion_saldo,
                       c.nombre AS concepto_nombre
                FROM payment_transfers pt
                JOIN usuarios u ON u.id = pt.usuario_id
                JOIN integrantes i ON i.id = pt.integrante_id
                JOIN obligaciones o ON o.id = pt.obligacion_id
                JOIN conceptos c ON c.id = o.concepto_id
                WHERE pt.id = :id
                """))
                .bind("id", id)
                .mapToMap()
                .findOne()
                .orElse(null));
    }

    public List<Map<String, Object>> list(String status, Long integranteId, int limit, int offset) {
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return jdbi.withHandle(handle -> richQuery(handle.createQuery("""
                SELECT pt.*, u.username, i.nombre_completo, i.tipo_documento, i.numero_documento,
                       i.telefono, i.correo, o.descripcion AS obligacion_descripcion,
                       o.estado AS obligacion_estado, o.saldo AS obligacion_saldo,
                       c.nombre AS concepto_nombre
                FROM payment_transfers pt
                JOIN usuarios u ON u.id = pt.usuario_id
                JOIN integrantes i ON i.id = pt.integrante_id
                JOIN obligaciones o ON o.id = pt.obligacion_id
                JOIN conceptos c ON c.id = o.concepto_id
                WHERE (:status IS NULL OR pt.status = :status)
                  AND (:integranteId IS NULL OR pt.integrante_id = :integranteId)
                ORDER BY pt.submitted_at DESC
                LIMIT :limit OFFSET :offset
                """))
                .bind("status", statusFilter)
                .bind("integranteId", integranteId)
                .bind("limit", limit)
                .bind("offset", offset)
                .mapToMap()
                .list());
    }

    public List<Map<String, Object>> history(Long transferId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT *
                FROM payment_transfer_events
                WHERE transfer_id = :transferId
                ORDER BY created_at DESC
                """)
                .bind("transferId", transferId)
                .mapToMap()
                .list());
    }

    public Map<String, Object> approve(Long id, String username, String observations) {
        return jdbi.inTransaction(handle -> {
            Map<String, Object> transfer = handle.createQuery("""
                    SELECT pt.*, o.saldo, o.estado AS obligacion_estado
                    FROM payment_transfers pt
                    JOIN obligaciones o ON o.id = pt.obligacion_id
                    WHERE pt.id = :id
                    FOR UPDATE
                    """)
                    .bind("id", id)
                    .mapToMap()
                    .findOne()
                    .orElseThrow(() -> new ApiException(404, "Pago por transferencia no encontrado"));

            String status = String.valueOf(transfer.get("status"));
            if (!"PENDING_VERIFICATION".equals(status)) {
                throw new ApiException(409, "El pago ya fue revisado");
            }

            BigDecimal amount = amountFrom(transfer.get("amount"));
            BigDecimal saldo = amountFrom(transfer.get("saldo"));
            BigDecimal applied = amount.min(saldo);
            Long obligationId = ((Number) transfer.get("obligacion_id")).longValue();
            String reference = String.valueOf(transfer.get("reference"));
            String receiptNumber = "WEPA-TR-" + java.time.LocalDate.now().getYear() + "-" + id;

            Long movementId = handle.createUpdate("""
                    INSERT INTO movimientos (obligacion_id, tipo, monto, descripcion)
                    VALUES (:obligacionId, 'INGRESO'::tipo_movimiento_enum, :amount, :description)
                    """)
                    .bind("obligacionId", obligationId)
                    .bind("amount", applied)
                    .bind("description", "Pago por transferencia verificado (" + reference + ")")
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one();

            handle.createUpdate("""
                    UPDATE obligaciones
                    SET saldo = GREATEST(0, saldo - :amount),
                        estado = CASE WHEN (saldo - :amount) <= 0 THEN 'PAGADO' ELSE 'PENDIENTE' END
                    WHERE id = :obligacionId
                    """)
                    .bind("amount", applied)
                    .bind("obligacionId", obligationId)
                    .execute();

            handle.createUpdate("""
                    UPDATE payment_transfers
                    SET status = 'APPROVED',
                        observations = :observations,
                        reviewed_by = :username,
                        reviewed_at = NOW(),
                        movement_id = :movementId,
                        receipt_number = :receiptNumber,
                        updated_at = NOW()
                    WHERE id = :id
                    """)
                    .bind("id", id)
                    .bind("observations", observations)
                    .bind("username", username)
                    .bind("movementId", movementId)
                    .bind("receiptNumber", receiptNumber)
                    .execute();

            handle.createUpdate("""
                    INSERT INTO payment_transfer_events (
                        transfer_id, action, username, observations, from_status, to_status, created_at
                    )
                    VALUES (:id, 'APPROVE', :username, :observations, 'PENDING_VERIFICATION', 'APPROVED', NOW())
                    """)
                    .bind("id", id)
                    .bind("username", username)
                    .bind("observations", observations)
                    .execute();

            return Map.of("movement_id", movementId, "receipt_number", receiptNumber);
        });
    }

    public void reject(Long id, String username, String observations) {
        jdbi.useTransaction(handle -> {
            Map<String, Object> transfer = handle.createQuery("""
                    SELECT *
                    FROM payment_transfers
                    WHERE id = :id
                    FOR UPDATE
                    """)
                    .bind("id", id)
                    .mapToMap()
                    .findOne()
                    .orElseThrow(() -> new ApiException(404, "Pago por transferencia no encontrado"));

            String status = String.valueOf(transfer.get("status"));
            if (!"PENDING_VERIFICATION".equals(status)) {
                throw new ApiException(409, "El pago ya fue revisado");
            }

            handle.createUpdate("""
                    UPDATE payment_transfers
                    SET status = 'REJECTED',
                        rejection_reason = :observations,
                        observations = :observations,
                        reviewed_by = :username,
                        reviewed_at = NOW(),
                        updated_at = NOW()
                    WHERE id = :id
                    """)
                    .bind("id", id)
                    .bind("observations", observations)
                    .bind("username", username)
                    .execute();

            handle.createUpdate("""
                    INSERT INTO payment_transfer_events (
                        transfer_id, action, username, observations, from_status, to_status, created_at
                    )
                    VALUES (:id, 'REJECT', :username, :observations, 'PENDING_VERIFICATION', 'REJECTED', NOW())
                    """)
                    .bind("id", id)
                    .bind("username", username)
                    .bind("observations", observations)
                    .execute();
        });
    }

    public void attachReceipt(Long id, String receiptPath) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                UPDATE payment_transfers
                SET receipt_path = :receiptPath,
                    updated_at = NOW()
                WHERE id = :id
                """)
                .bind("id", id)
                .bind("receiptPath", receiptPath)
                .execute());
    }

    public void attachMovementDocuments(Long transferId, Long movementId, String receiptName, String receiptPath) {
        jdbi.useHandle(handle -> {
            Map<String, Object> transfer = handle.createQuery("""
                    SELECT pt.*, u.username AS submitted_by
                    FROM payment_transfers pt
                    LEFT JOIN usuarios u ON u.id = pt.usuario_id
                    WHERE pt.id = :transferId
                    """)
                    .bind("transferId", transferId)
                    .mapToMap()
                    .findOne()
                    .orElseThrow(() -> new ApiException(404, "Pago por transferencia no encontrado"));

            handle.createUpdate("""
                    INSERT INTO documentos (
                        nombre_archivo, ruta_url, tipo_documento, entidad_tipo, entidad_id,
                        original_filename, mime_type, file_size_bytes, folder_path,
                        estado, validado_por, validado_en, observaciones, uploaded_by, created_at
                    )
                    SELECT
                        :supportName, :supportPath, 'SOPORTE_EXTERNO', 'MOVIMIENTO', :movementId,
                        :supportName, :supportMime, :supportSize, 'payment-transfers',
                        'VALIDADO', :reviewedBy, COALESCE(CAST(:reviewedAt AS timestamp), NOW()),
                        'Soporte de transferencia validado junto con el pago', :username, NOW()
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM documentos
                        WHERE entidad_tipo = 'MOVIMIENTO'
                          AND entidad_id = :movementId
                          AND tipo_documento = 'SOPORTE_EXTERNO'
                          AND ruta_url = :supportPath
                    )
                    """)
                    .bind("movementId", movementId)
                    .bind("supportName", transfer.get("support_original_filename"))
                    .bind("supportPath", transfer.get("support_path"))
                    .bind("supportMime", transfer.get("support_mime_type"))
                    .bind("supportSize", transfer.get("support_size_bytes"))
                    .bind("reviewedBy", transfer.get("reviewed_by"))
                    .bind("reviewedAt", transfer.get("reviewed_at"))
                    .bind("username", transfer.get("submitted_by"))
                    .execute();

            handle.createUpdate("""
                    INSERT INTO documentos (
                        nombre_archivo, ruta_url, tipo_documento, entidad_tipo, entidad_id,
                        original_filename, mime_type, folder_path, estado, validado_por,
                        validado_en, observaciones, uploaded_by, created_at
                    )
                    SELECT
                        :receiptName, :receiptPath, 'RECIBO_SISTEMA', 'MOVIMIENTO', :movementId,
                        :receiptName, 'application/pdf', 'recibos-sistema', 'VALIDADO', :reviewedBy,
                        COALESCE(CAST(:reviewedAt AS timestamp), NOW()), 'Recibo generado y validado automaticamente por WEPA',
                        :reviewedBy, NOW()
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM documentos
                        WHERE entidad_tipo = 'MOVIMIENTO'
                          AND entidad_id = :movementId
                          AND tipo_documento = 'RECIBO_SISTEMA'
                          AND ruta_url = :receiptPath
                    )
                    """)
                    .bind("movementId", movementId)
                    .bind("receiptName", receiptName)
                    .bind("receiptPath", receiptPath)
                    .bind("reviewedBy", transfer.get("reviewed_by"))
                    .bind("reviewedAt", transfer.get("reviewed_at"))
                    .execute();
        });
    }

    private org.jdbi.v3.core.statement.Query richQuery(org.jdbi.v3.core.statement.Query query) {
        return query;
    }

    private BigDecimal amountFrom(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
