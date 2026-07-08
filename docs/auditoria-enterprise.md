# Auditoria Enterprise WEPA

## Problemas Encontrados

- Documental solo tenia carga/listado base; faltaban tipos, estados, validacion, dashboard, versionado visible y busqueda paginada.
- Pagos estaba desacoplado, pero solo aceptaba `WOMPI` y `EPAYCO`; `MERCADOPAGO` fallaba por enum y constraints SQL.
- RBAC estaba limitado al campo `rol`; no existia matriz de permisos ni validacion puntual por modulo nuevo.
- No existia `audit_logs` para trazabilidad enterprise de pagos/documentos.
- Frontend tenia estilos y pantallas legacy sin componentes base reutilizables.
- Las tablas nuevas necesitaban server-side pagination y loaders reutilizables.
- `/api2` se mantenia en frontend; en desarrollo ahora se reescribe a `/api` desde Vite para compatibilidad.

## Mejoras Aplicadas

- Flyway `V2__documentos_pagos_rbac_auditoria.sql` con `document_types`, estados documentales, `audit_logs`, indices e inclusion de `MERCADOPAGO`.
- Backend documental extendido con busqueda, dashboard, vencidos, faltantes, tipos, validacion, estados y versiones.
- Storage desacoplado con `StorageProvider`, `LocalStorageProvider`, stubs MinIO y S3.
- Auditoria en subida, preview, descarga, validacion, cambios de estado, pagos y webhooks.
- RBAC con permisos `documentos.*`, `pagos.*`, `finanzas.ver`, `dashboard.ver`.
- MercadoPago agregado como provider y webhook especifico `/api/payments/webhooks/mercadopago`.
- Frontend con `src/components/ui`, stores enterprise, composables, modulo documental y modulo pagos.
- Build backend y frontend validados.

## TODO Priorizado

1. Conectar llamadas HTTP reales de creacion de checkout y reversos contra SDK/API oficial de Wompi, ePayco y MercadoPago.
2. Agregar pruebas de integracion con PostgreSQL para migraciones, RBAC, documental y webhooks.
3. Implementar refresh token web; hoy el refresh enterprise esta disponible para mobile.
4. Crear reverse proxy productivo que reescriba `/api2` a `/api` si se conserva ese path legacy.
5. Agregar monitoreo de HikariCP, latencia por endpoint y tamano de uploads.
6. Implementar MinIO o S3 real si se requiere despliegue multi-instancia.
7. Agregar rate limiting por IP/usuario en login, uploads y webhooks.

## Recomendaciones Produccion

- Ejecutar migraciones primero en staging con backup PostgreSQL.
- Usar `WEPA_PUBLIC_UPLOADS_ENABLED=false` y servir archivos solo por endpoints autenticados.
- Configurar HTTPS, HSTS y reverse proxy con limites de upload.
- Rotar `JWT_SECRET` y secretos de webhooks fuera del repositorio.
- Separar credenciales por ambiente y usar backups diarios con prueba de restauracion.
