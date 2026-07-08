UPDATE documentos
SET estado = 'VALIDADO',
    validado_por = COALESCE(validado_por, 'SISTEMA'),
    validado_en = COALESCE(validado_en, NOW()),
    observaciones = COALESCE(observaciones, 'Soporte validado automaticamente al registrar movimiento aplicado')
WHERE entidad_tipo = 'MOVIMIENTO'
  AND tipo_documento = 'SOPORTE_EXTERNO'
  AND estado IS DISTINCT FROM 'VALIDADO';
