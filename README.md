# WEPA Backend

Sistema administrativo, contable, documental y de pagos para organizaciones culturales.

Este backend usa Java 17, Javalin, JDBI, PostgreSQL, HikariCP, Flyway, JWT y BCrypt.

## Estado Actual

Se dejaron implementadas estas bases enterprise:

- Configuracion por variables de entorno y archivos `.env`.
- Pool de conexiones con HikariCP.
- Migraciones SQL versionadas con Flyway.
- JWT configurable y endpoints moviles con refresh tokens.
- Manejo centralizado de errores JSON.
- Rutas de login compatibles: `/api/login`, `/api2/login` y `/login`.
- Arquitectura desacoplada para pagos con `PaymentProvider`.
- Providers iniciales para `WOMPI`, `EPAYCO` y `MERCADOPAGO`.
- Webhooks de pagos con secreto compartido.
- Pagos por transferencia con soporte, revision admin, historial y recibo PDF.
- Gestion documental segura con versionado, hash, MIME y tamano maximo.
- API movil versionada en `/api/mobile/v1`.
- Usuarios moviles asociados explicitamente a integrantes.
- Dockerfile y `docker-compose.yml`.
- OpenAPI movil en `docs/openapi-mobile-v1.yaml`.
- Manual de pagos por transferencia en `docs/pagos-transferencia.md`.
- Roadmap en `docs/roadmap-tecnico.md`.

## Estructura Principal

```text
src/main/java/ETI/sgc
  App.java                         # Bootstrap Javalin, seguridad y rutas
  config/
    AppConfig.java                 # Variables de entorno y .env
    Database.java                  # HikariCP, Flyway, JDBI
  controller/                      # Controladores existentes
  dao/                             # Acceso a datos existente
  document/                        # Gestion documental nueva
  error/                           # Errores API consistentes
  http/                            # Utilidades HTTP/paginacion
  mobile/                          # API Flutter/mobile
  payment/                         # Pagos, webhooks y providers
  security/JwtUtil.java            # JWT configurable
src/main/resources/db/migration
  V1__enterprise_foundation.sql    # Tablas nuevas, columnas e indices
docs/
  openapi-mobile-v1.yaml
  pagos-transferencia.md
  roadmap-tecnico.md
```

## Requisitos

- Java 17
- Maven 3.9+
- PostgreSQL 16
- Base de datos `wepa`

## Configuracion

Copia `.env.example` a `.env` y ajusta valores:

```properties
APP_ENV=local
APP_PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/wepa
DB_USER=postgres
DB_PASSWORD=123
JWT_SECRET=CAMBIAR_ESTA_CLAVE_SUPER_LARGA_Y_SEGURA_MINIMO_32_CHARS
UPLOAD_BASE_DIR=uploads
```

Para staging y produccion usa:

- `.env.staging.example`
- `.env.production.example`

Importante en produccion:

- `WEPA_BOOTSTRAP_ADMIN_ENABLED=false`
- `WEPA_PUBLIC_UPLOADS_ENABLED=false`
- `JWT_SECRET` largo y privado
- passwords reales fuera de Git

Para recibos de transferencia:

```properties
TRANSFER_SUPPORT_MAX_FILE_SIZE_BYTES=10485760
WEPA_RECEIPT_LOGO_PATH=../WEPA-FRONT/src/assets/logo.PNG
```

## Ejecutar Local

```bash
mvn package
java -jar target/SGC1.0-1.0-SNAPSHOT.jar
```

El backend queda en:

```text
http://localhost:8080
```

Al iniciar, Flyway aplica migraciones pendientes automaticamente.

## Docker

```bash
docker compose up --build
```

Servicios:

- Backend: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

## Autenticacion

Login web:

```http
POST /api/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

Respuesta:

```json
{
  "token": "...",
  "rol": "ADMIN",
  "username": "admin"
}
```

Usar token:

```http
Authorization: Bearer TOKEN
```

## Endpoints Principales

### Administracion

- `GET /api/usuarios`
- `POST /api/usuarios`
- `PATCH /api/usuarios/{id}/estado`
- `PATCH /api/usuarios/{id}/reset-password`
- `GET /api/admin/conceptos`
- `POST /api/admin/conceptos`

### Terceros

- `GET /api/admin/integrantes`
- `POST /api/admin/integrantes`
- `PUT /api/admin/integrantes/{id}`
- `PATCH /api/admin/integrantes/{id}/estado`
- `PATCH /api/admin/integrantes/{id}/foto`
- `GET /api/admin/integrantes/{id}/usuario`
- `POST /api/admin/integrantes/{id}/usuario`
- `PATCH /api/admin/integrantes/{id}/usuario/estado`
- `PATCH /api/admin/integrantes/{id}/usuario/password`
- `DELETE /api/admin/integrantes/{id}/usuario`
- `GET /api/admin/integrantes/{id}/foto`
- `GET /api/proveedores`
- `GET /api/patrocinadores`
- `GET /api/entidades`

### Finanzas

- `POST /api/finanzas/obligaciones`
- `POST /api/finanzas/obligaciones-masivas`
- `POST /api/finanzas/movimientos`
- `GET /api/finanzas/movimientos`
- `POST /api/finanzas/movimientos/reversar/{id}`
- `GET /api/finanzas/dashboard/resumen`

## Pagos

La arquitectura usa:

```text
PaymentProvider
  WompiPaymentProvider
  EpaycoPaymentProvider
  MercadoPagoPaymentProvider
```

Iniciar pago:

```http
POST /api/payments/initiate
Authorization: Bearer TOKEN
Content-Type: application/json

{
  "provider": "WOMPI",
  "obligacion_id": 1,
  "amount": 50000,
  "currency": "COP",
  "method": "PSE",
  "payer_email": "cliente@mail.com",
  "payer_document": "123456"
}
```

Webhook:

```http
POST /api/payments/webhooks/wompi
X-WEPA-Webhook-Secret: secreto_configurado
Content-Type: application/json

{
  "reference": "wompi-1-uuid",
  "status": "APPROVED"
}
```

Estados soportados:

- `PENDIENTE`
- `APROBADO`
- `RECHAZADO`
- `EXPIRADO`

Cuando un webhook llega como aprobado, el sistema registra el movimiento y descuenta saldo de la obligacion de forma transaccional.

## Gestion Documental

Subir documento:

```http
POST /api/documentos
Authorization: Bearer TOKEN
Content-Type: multipart/form-data

file=@archivo.pdf
entidad_tipo=INTEGRANTE
entidad_id=1
tipo_documento=CONTRATO
folder_path=integrantes/contratos
```

Consultar por entidad:

```http
GET /api/documentos/entidad/INTEGRANTE/1
```

Descargar:

```http
GET /api/documentos/{id}/download
```

Vista previa:

```http
GET /api/documentos/{id}/preview
```

Validaciones:

- PDF, PNG, JPG/JPEG y WEBP.
- Tamano maximo por `UPLOAD_MAX_FILE_SIZE_BYTES`.
- SHA-256 por archivo.
- Version documental por entidad/tipo/carpeta.
- Descarga autenticada por API.

## API Mobile Flutter

Base:

```text
/api/mobile/v1
```

Los usuarios con rol `INTEGRANTE` deben estar asociados a `usuarios.integrante_id`. Con esa asociacion, el login movil devuelve `user.integrante_id` y la API limita pagos/documentos a ese integrante para evitar cruces de informacion.

Endpoints:

- `POST /login`
- `POST /refresh`
- `POST /logout`
- `GET /perfil`
- `GET /pagos?integrante_id=1`
- `GET /pagos/pendientes?integrante_id=1`
- `GET /pagos/{integranteId}/movimientos`
- `GET /qr-nfc/{codigo}`
- `GET /documentos/{entidadTipo}/{entidadId}`
- `GET /asistencia`
- `GET /notificaciones`
- `POST /push-token`
- `DELETE /push-token`

OpenAPI:

```text
docs/openapi-mobile-v1.yaml
```

## Base De Datos

Migracion aplicada:

```text
V1__enterprise_foundation.sql
V2__documentos_pagos_rbac_auditoria.sql
V3__usuarios_integrantes_mobile.sql
V4__mobile_push_tokens.sql
```

Agrega:

- Columnas documentales: version, hash, MIME, tamano, carpeta, usuario.
- `payment_transactions`
- `payment_events`
- `mobile_refresh_tokens`
- `usuarios.integrante_id` con FK a `integrantes(id)` e indice unico parcial.
- `mobile_push_tokens` para tokens FCM/APNs por usuario e integrante.
- Indices para obligaciones, movimientos, documentos y busquedas `ILIKE`.
- Extension `pg_trgm`.

## Verificacion

Backend:

```bash
mvn test
mvn package
```

Frontend:

```bash
npm run build
npm audit --omit=dev
```

Resultados actuales:

- Backend compila y empaqueta jar ejecutable.
- Frontend compila con Vite.
- Auditoria de produccion frontend: 0 vulnerabilidades.
- Flyway arranco y aplico `V1` en PostgreSQL local.

## Recomendaciones DevOps

- Versionar backend y frontend en Git antes del siguiente bloque de cambios.
- Activar backups diarios de PostgreSQL.
- No publicar `/uploads` directamente en produccion; usar endpoints autenticados.
- Separar `.env` por ambiente.
- Usar HTTPS y reverse proxy.
- Rotar `JWT_SECRET` y secretos de webhooks.
- Configurar logs centralizados.
- Monitorear pool HikariCP y conexiones PostgreSQL.
- Ejecutar migraciones primero en staging.

## Pendientes Priorizados

1. Implementar llamadas reales HTTP a Wompi/ePayco con sus firmas oficiales.
2. Agregar pruebas reales de pagos, webhooks, documentos y concurrencia.
3. Convertir endpoints listados a paginacion server-side completa.
4. Reforzar roles por endpoint, no solo autenticacion.
5. Integrar FCM/APNs para notificaciones push.
6. Reemplazar uploads publicos en frontend por URLs autenticadas.

## Actualizacion Enterprise 2026-05-28

Se agrego la migracion `V2__documentos_pagos_rbac_auditoria.sql` con `document_types`, estados documentales, `audit_logs`, indices documentales y soporte SQL para `MERCADOPAGO`.

Nuevos modulos backend:

- `audit/AuditDAO.java`
- `security/Rbac.java`
- `document/storage/*`
- `payment/providers/MercadoPagoPaymentProvider.java`

Nuevos endpoints documentales:

- `GET /api/documentos/search`
- `GET /api/documentos/dashboard`
- `GET /api/documentos/vencidos`
- `GET /api/documentos/faltantes`
- `GET /api/documentos/tipos`
- `POST /api/documentos/tipos`
- `PATCH /api/documentos/{id}/estado`
- `PATCH /api/documentos/{id}/validar`
- `GET /api/documentos/{id}/versiones`

Pagos:

- `MERCADOPAGO` queda disponible como `PaymentProviderType`.
- Webhook dedicado: `POST /api/payments/webhooks/mercadopago`.
- Reverso local preparado: `POST /api/payments/{id}/reverse`.

RBAC:

- Roles: `ADMIN`, `CONTADOR`, `TESORERO`, `DOCENTE`, `INTEGRANTE`, `AUDITOR`.
- Permisos: `documentos.ver`, `documentos.subir`, `documentos.validar`, `pagos.crear`, `pagos.reversar`, `finanzas.ver`, `dashboard.ver`.

Documentacion adicional:

- `docs/auditoria-enterprise.md`
- `docs/openapi-enterprise-v1.yaml`

## Actualizacion Mobile Integrantes 2026-05-28

Se agrego la migracion `V3__usuarios_integrantes_mobile.sql` para asociar usuarios moviles a integrantes mediante `usuarios.integrante_id`.

Gestion backend:

- `GET /api/admin/integrantes/{id}/usuario`
- `POST /api/admin/integrantes/{id}/usuario`
- `PATCH /api/admin/integrantes/{id}/usuario/estado`
- `PATCH /api/admin/integrantes/{id}/usuario/password`
- `DELETE /api/admin/integrantes/{id}/usuario`

Seguridad movil:

- El JWT ahora incluye `integrante_id`.
- El login y perfil movil devuelven `user.integrante_id`.
- Un usuario con rol `INTEGRANTE` solo puede consultar sus propios pagos y documentos.
- La app Flutter guarda el `integrante_id` real y lo usa para listar obligaciones.

## Actualizacion Seguridad Y Push 2026-05-28

RBAC por endpoint:

- Usuarios: `usuarios.gestionar`
- Terceros: `terceros.ver`, `terceros.gestionar`
- Finanzas/pagos: `finanzas.ver`, `pagos.crear`, `pagos.reversar`
- Configuracion: `configuracion.gestionar`
- Documentos: `documentos.ver`, `documentos.subir`, `documentos.validar`

App movil:

- El login movil rechaza usuarios que no sean `INTEGRANTE`.
- El login movil rechaza usuarios sin `integrante_id`.
- Se agrego `mobile_push_tokens` y endpoints `/api/mobile/v1/push-token` para registrar/desactivar tokens FCM/APNs.

Uploads:

- `WEPA_PUBLIC_UPLOADS_ENABLED` ahora queda desactivado por defecto.
- Las fotos de integrantes se leen por `GET /api/admin/integrantes/{id}/foto` con JWT.
- Los documentos y soportes se descargan por `/api/documentos/{id}/download` con JWT.
