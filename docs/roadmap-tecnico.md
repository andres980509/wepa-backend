# Roadmap Tecnico WEPA

## Fase 0 - Base Segura
- Crear repositorios Git para `C:\WEPA` y `C:\WEPA-FRONT`.
- Crear backups SQL antes de migraciones productivas.
- Usar `.env` por entorno y no commitear credenciales reales.
- Ejecutar `mvn test` y `npm run build` antes de desplegar.

## Fase 1 - Backend Enterprise
- Pool de conexiones con HikariCP.
- Migraciones Flyway versionadas.
- JWT configurable por entorno.
- Middleware centralizado y errores JSON consistentes.
- Dockerfile y Docker Compose.

## Fase 2 - Multiusuario y Datos
- Paginacion real en endpoints pesados.
- Indices para filtros frecuentes.
- Transacciones con un solo handle JDBI.
- Bloqueos `FOR UPDATE` en pagos y reversos.
- Idempotencia para webhooks y reversos.

## Fase 3 - Pagos
- Interfaz `PaymentProvider`.
- Providers desacoplados para Wompi y ePayco.
- Tablas de transacciones y eventos.
- Webhooks con secreto compartido.
- Estados normalizados: `PENDIENTE`, `APROBADO`, `RECHAZADO`, `EXPIRADO`.

## Fase 4 - Documentos
- Carga segura con MIME/tamano/hash.
- Versiones documentales.
- Asociacion por entidad.
- Descarga y preview autenticados.
- Carpetas logicas.

## Fase 5 - Movil Flutter
- API `/api/mobile/v1`.
- Access token corto y refresh token revocable.
- Endpoints para pagos, QR/NFC, documentos, asistencia y notificaciones.
- OpenAPI en `docs/openapi-mobile-v1.yaml`.

## Fase 6 - Frontend
- Variables Vite.
- Cliente API unico.
- Tokens visuales de marca.
- Componentes reutilizables.
- Paginacion server-side progresiva.

## Fase 7 - DevOps
- Staging y produccion separados.
- Backups automatizados.
- Logs centralizados.
- Health checks.
- Rotacion de secretos.
