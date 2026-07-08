# Pagos por transferencia WEPA

Flujo manual para integrantes que pagan por transferencia bancaria y cargan soporte PDF/imagen. El pago no se aprueba automaticamente: queda en revision administrativa.

## Estados

- `PENDING_VERIFICATION`: soporte recibido, pendiente de revisar.
- `APPROVED`: aprobado por administracion; descuenta saldo de la obligacion, crea movimiento de ingreso y genera recibo PDF.
- `REJECTED`: rechazado con observaciones.

## App movil

La app usa el integrante asociado al JWT. No envia ni muestra IDs internos de integrante.

Endpoints:

- `GET /api/mobile/v1/pagos`
- `GET /api/mobile/v1/pagos/pendientes`
- `GET /api/mobile/v1/pagos/movimientos`
- `POST /api/mobile/v1/payments/transfer`
- `GET /api/mobile/v1/payments/transfer`
- `GET /api/mobile/v1/payments/transfer/{id}/support`
- `GET /api/mobile/v1/payments/transfer/{id}/receipt`

Multipart para crear transferencia:

```text
obligacion_id=<id interno de obligacion seleccionado por la app>
amount=1000
method=Transferencia bancaria
bank=Bancolombia
reference=ABC123456
support=@comprobante.pdf
```

El backend valida que la obligacion pertenezca al integrante autenticado y que el monto no supere el saldo.

## Panel admin

Ruta frontend:

```text
/admin/transferencias
```

Permiso requerido:

```text
pagos.validar
```

Endpoints:

- `GET /api/payments/transfers?status=PENDING_VERIFICATION&page=1&size=25`
- `GET /api/payments/transfers/{id}`
- `GET /api/payments/transfers/{id}/history`
- `GET /api/payments/transfers/{id}/support`
- `PATCH /api/payments/transfers/{id}/approve`
- `PATCH /api/payments/transfers/{id}/reject`
- `GET /api/payments/transfers/{id}/receipt`

## Storage y validaciones

Variables:

```properties
UPLOAD_BASE_DIR=uploads
TRANSFER_SUPPORT_MAX_FILE_SIZE_BYTES=10485760
WEPA_RECEIPT_LOGO_PATH=../WEPA-FRONT/src/assets/logo.PNG
```

Tipos permitidos:

- `application/pdf`
- `image/png`
- `image/jpeg`
- `image/webp`

Los soportes se guardan en:

```text
uploads/payment-transfers/supports/yyyyMM
```

Los recibos se guardan en:

```text
uploads/payment-transfers/receipts/yyyyMM
```

## Prueba local

1. Levantar backend:

```powershell
cd C:\WEPA
java -jar target\SGC1.0-1.0-SNAPSHOT.jar
```

2. Entrar en la app movil con un usuario `INTEGRANTE`.
3. Ir a `Pagos`, seleccionar una obligacion pendiente y escoger `Transferencia bancaria`.
4. Cargar PDF/imagen, banco/metodo y referencia.
5. Entrar en web como admin y abrir `/admin/transferencias`.
6. Revisar soporte, aprobar o rechazar.
7. Si se aprueba, descargar el recibo PDF.
