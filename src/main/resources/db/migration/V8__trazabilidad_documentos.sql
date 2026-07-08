UPDATE documentos d
SET uploaded_by = COALESCE(u.username, d.uploaded_by, d.validado_por, 'SISTEMA')
FROM payment_transfers pt
LEFT JOIN usuarios u ON u.id = pt.usuario_id
WHERE d.ruta_url = pt.support_path
  AND d.tipo_documento = 'SOPORTE_EXTERNO'
  AND d.entidad_tipo = 'MOVIMIENTO'
  AND (d.uploaded_by IS NULL OR d.uploaded_by = d.validado_por);

UPDATE documentos
SET uploaded_by = COALESCE(uploaded_by, validado_por, 'SISTEMA')
WHERE uploaded_by IS NULL
  AND tipo_documento IN ('SOPORTE_EXTERNO', 'RECIBO_SISTEMA');
