UPDATE documentos
SET estado = 'VALIDADO',
    validado_por = COALESCE(validado_por, 'SISTEMA'),
    validado_en = COALESCE(validado_en, created_at, NOW()),
    observaciones = COALESCE(observaciones, 'Recibo generado y validado automaticamente por WEPA'),
    original_filename = COALESCE(original_filename, nombre_archivo),
    mime_type = COALESCE(mime_type, 'application/pdf'),
    folder_path = COALESCE(folder_path, 'recibos-sistema')
WHERE tipo_documento = 'RECIBO_SISTEMA';

INSERT INTO documentos (
    nombre_archivo,
    ruta_url,
    tipo_documento,
    entidad_tipo,
    entidad_id,
    original_filename,
    mime_type,
    file_size_bytes,
    folder_path,
    estado,
    validado_por,
    validado_en,
    observaciones,
    uploaded_by,
    created_at
)
SELECT
    pt.support_original_filename,
    pt.support_path,
    'SOPORTE_EXTERNO',
    'MOVIMIENTO',
    pt.movement_id,
    pt.support_original_filename,
    pt.support_mime_type,
    pt.support_size_bytes,
    'payment-transfers',
    'VALIDADO',
    COALESCE(pt.reviewed_by, 'SISTEMA'),
    COALESCE(pt.reviewed_at, pt.updated_at, NOW()),
    'Soporte de transferencia validado junto con el pago',
    COALESCE(pt.reviewed_by, u.username, 'SISTEMA'),
    COALESCE(pt.submitted_at, NOW())
FROM payment_transfers pt
JOIN usuarios u ON u.id = pt.usuario_id
WHERE pt.status = 'APPROVED'
  AND pt.movement_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM documentos d
      WHERE d.entidad_tipo = 'MOVIMIENTO'
        AND d.entidad_id = pt.movement_id
        AND d.tipo_documento = 'SOPORTE_EXTERNO'
        AND d.ruta_url = pt.support_path
  );

INSERT INTO documentos (
    nombre_archivo,
    ruta_url,
    tipo_documento,
    entidad_tipo,
    entidad_id,
    original_filename,
    mime_type,
    folder_path,
    estado,
    validado_por,
    validado_en,
    observaciones,
    uploaded_by,
    created_at
)
SELECT
    COALESCE(pt.receipt_number || '.pdf', 'recibo_transferencia_' || pt.id || '.pdf'),
    pt.receipt_path,
    'RECIBO_SISTEMA',
    'MOVIMIENTO',
    pt.movement_id,
    COALESCE(pt.receipt_number || '.pdf', 'recibo_transferencia_' || pt.id || '.pdf'),
    'application/pdf',
    'recibos-sistema',
    'VALIDADO',
    COALESCE(pt.reviewed_by, 'SISTEMA'),
    COALESCE(pt.reviewed_at, pt.updated_at, NOW()),
    'Recibo generado y validado automaticamente por WEPA',
    COALESCE(pt.reviewed_by, 'SISTEMA'),
    COALESCE(pt.reviewed_at, pt.updated_at, NOW())
FROM payment_transfers pt
WHERE pt.status = 'APPROVED'
  AND pt.movement_id IS NOT NULL
  AND pt.receipt_path IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM documentos d
      WHERE d.entidad_tipo = 'MOVIMIENTO'
        AND d.entidad_id = pt.movement_id
        AND d.tipo_documento = 'RECIBO_SISTEMA'
        AND d.ruta_url = pt.receipt_path
  );
