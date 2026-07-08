package ETI.sgc.controller;

import ETI.sgc.config.AppConfig;
import ETI.sgc.dao.MovimientoDAO;
import ETI.sgc.dao.ObligacionDAO;
import ETI.sgc.dao.ValidacionConceptoDAO;
import ETI.sgc.dto.ObligacionMasivaRequest;
import ETI.sgc.dto.ObligacionRequest;
import ETI.sgc.dto.RegistroPagoDTO;
import ETI.sgc.payment.receipt.GeneratedReceipt;
import ETI.sgc.payment.receipt.PaymentReceiptData;
import ETI.sgc.payment.receipt.PaymentReceiptService;
import ETI.sgc.security.Rbac;
import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import org.jdbi.v3.core.Jdbi;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FinanzasController {
    private static final Set<String> ALLOWED_SUPPORT_MIME_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private final Jdbi jdbi;
    private final ObligacionDAO obligacionDAO;
    private final MovimientoDAO movimientoDAO;
    private final ValidacionConceptoDAO validacionDAO;
    private final PaymentReceiptService receiptService;
    private final Path uploadBasePath;
    private final long maxSupportFileSizeBytes;

    public FinanzasController(Jdbi jdbi, ObligacionDAO oDAO, MovimientoDAO mDAO, ValidacionConceptoDAO vDAO) {
        this(jdbi, oDAO, mDAO, vDAO, AppConfig.load());
    }

    public FinanzasController(Jdbi jdbi, ObligacionDAO oDAO, MovimientoDAO mDAO, ValidacionConceptoDAO vDAO, AppConfig appConfig) {
        this.jdbi = jdbi;
        this.obligacionDAO = oDAO;
        this.movimientoDAO = mDAO;
        this.validacionDAO = vDAO;
        this.receiptService = new PaymentReceiptService(appConfig);
        this.uploadBasePath = Path.of(appConfig.get("UPLOAD_BASE_DIR", "uploads")).toAbsolutePath().normalize();
        this.maxSupportFileSizeBytes = appConfig.getLong("PAYMENT_SUPPORT_MAX_FILE_SIZE_BYTES", 10L * 1024L * 1024L);
    }

    public void routes(Javalin app) {
        app.post("/api/finanzas/obligaciones", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_CREAR);
            ObligacionRequest req = ctx.bodyAsClass(ObligacionRequest.class);

            if (!validacionDAO.existe(req.concepto_id, req.tipo_tercero)) {
                ctx.status(400).json(Map.of("error", "Este concepto no es valido para este tipo de tercero"));
                return;
            }

            Long id = obligacionDAO.crear(
                    req.concepto_id,
                    req.tipo_tercero,
                    req.tercero_id,
                    req.monto_total,
                    req.descripcion
            );

            ctx.status(201).json(Map.of("id", id, "mensaje", "Obligacion registrada con exito"));
        });

        app.get("/api/finanzas/obligaciones/tercero/{tipo}/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.FINANZAS_VER);
            String tipo = ctx.pathParam("tipo");
            Long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(obligacionDAO.listarPorTercero(id, tipo));
        });

        app.post("/api/finanzas/movimientos", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_CREAR);
            Long obligacionId = Long.parseLong(ctx.formParam("obligacion_id"));
            BigDecimal monto = new BigDecimal(ctx.formParam("monto"));
            String tipo = valueOrDefault(ctx.formParam("tipo"), "INGRESO");
            String descripcion = valueOrDefault(ctx.formParam("descripcion"), "Pago registrado");
            String metodoPago = valueOrDefault(ctx.formParam("metodo_pago"), "Pago registrado en plataforma");
            String referenciaPago = valueOrDefault(ctx.formParam("referencia_pago"), "Registro web " + metodoPago);
            String username = valueOrDefault(ctx.attribute("username"), "SISTEMA");

            String comprobanteNombre = null;
            String comprobanteRutaBD = null;
            UploadedFile comprobanteUser = ctx.uploadedFile("comprobante_adjunto");
            if (comprobanteUser != null) {
                StoredPaymentSupport storedSupport = storeSupport(comprobanteUser);
                comprobanteNombre = storedSupport.originalFilename();
                comprobanteRutaBD = storedSupport.relativePath();
            }

            GeneratedReceipt receipt = receiptService.generate(receiptData(
                    obligacionId,
                    monto,
                    metodoPago,
                    "WEB",
                    referenciaPago,
                    username,
                    descripcion
            ));

            RegistroPagoDTO datosPago = new RegistroPagoDTO(
                    obligacionId,
                    monto,
                    tipo,
                    descripcion,
                    metodoPago,
                    comprobanteNombre,
                    comprobanteRutaBD,
                    receipt.filename,
                    receipt.relativePath,
                    username
            );

            Long movimientoId = obligacionDAO.registrarPagoCompletoConDocumentos(datosPago);

            ctx.json(Map.of(
                    "mensaje", "Pago aplicado y documentos archivados correctamente",
                    "movimiento_id", movimientoId,
                    "recibo", receipt.receiptNumber
            ));
        });

        app.get("/api/finanzas/movimientos/tercero/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.FINANZAS_VER);
            try {
                Long terceroId = Long.parseLong(ctx.pathParam("id"));
                String tipoTercero = ctx.queryParam("tipo");
                if (tipoTercero == null || tipoTercero.isBlank()) {
                    tipoTercero = "INTEGRANTE";
                }
                ctx.json(movimientoDAO.listarPorTercero(terceroId, tipoTercero));
            } catch (NumberFormatException e) {
                ctx.status(400).json(Map.of("error", "El ID proporcionado no es un numero valido"));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Error interno en servidor: " + e.getMessage()));
            }
        });

        app.post("/api/finanzas/obligaciones-masivas", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_CREAR);
            ObligacionMasivaRequest req = ctx.bodyAsClass(ObligacionMasivaRequest.class);
            if (req.terceros == null || req.terceros.isEmpty()) {
                ctx.status(400).json(Map.of("error", "La lista de terceros no puede estar vacia"));
                return;
            }

            jdbi.useTransaction(handle -> {
                for (Long terceroId : req.terceros) {
                    obligacionDAO.crear(
                            handle,
                            req.concepto_id,
                            req.tipo_tercero,
                            terceroId,
                            req.monto_total,
                            req.descripcion
                    );
                }
            });

            ctx.status(201).json(Map.of(
                    "mensaje", "Se han creado " + req.terceros.size() + " obligaciones exitosamente."
            ));
        });

        app.post("/api/finanzas/movimientos/reversar/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_REVERSAR);
            try {
                Long movimientoId = Long.parseLong(ctx.pathParam("id"));
                String motivo = ctx.queryParam("motivo");

                if (motivo == null || motivo.isBlank()) {
                    ctx.status(400).json(Map.of("error", "Debe proporcionar un motivo para la reversion"));
                    return;
                }

                Map<String, Object> info = jdbi.withHandle(handle ->
                        handle.createQuery("""
                                SELECT o.tipo_tercero
                                FROM movimientos m
                                JOIN obligaciones o ON m.obligacion_id = o.id
                                WHERE m.id = :id
                                """)
                                .bind("id", movimientoId)
                                .mapToMap()
                                .findOne()
                                .orElseThrow(() -> new RuntimeException("No se encontro la informacion del movimiento"))
                );

                String tipoTercero = info.get("tipo_tercero").toString();
                if ("INTEGRANTE".equalsIgnoreCase(tipoTercero)) {
                    movimientoDAO.reversarAbonoIntegrante(movimientoId, motivo);
                } else {
                    movimientoDAO.reversarGastoEmpresa(movimientoId, motivo);
                }

                ctx.json(Map.of(
                        "mensaje", "Movimiento reversado correctamente",
                        "tipo_aplicado", tipoTercero
                ));
            } catch (RuntimeException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Error interno: " + e.getMessage()));
            }
        });

        app.get("/api/finanzas/movimientos", ctx -> {
            Rbac.requirePermission(ctx, Rbac.FINANZAS_VER);
            String inicio = ctx.queryParam("inicio");
            String fin = ctx.queryParam("fin");
            String tipo = ctx.queryParam("tipo");
            ctx.json(movimientoDAO.listarConFiltros(inicio, fin, tipo));
        });

        app.post("/api/finanzas/ingreso-directo-full", ctx -> {
            Rbac.requirePermission(ctx, Rbac.PAGOS_CREAR);
            Map<String, Object> req = ctx.bodyAsClass(Map.class);
            try {
                movimientoDAO.registrarIngresoDirectoCompleto(req);
                ctx.status(201).json(Map.of(
                        "status", "success",
                        "mensaje", "Ingreso y concepto registrados correctamente"
                ));
            } catch (Exception e) {
                ctx.status(500).json(Map.of(
                        "status", "error",
                        "mensaje", "Error al procesar la transaccion: " + e.getMessage()
                ));
            }
        });

        app.get("/api/finanzas/obligaciones/integrante/{id}", ctx -> {
            Rbac.requirePermission(ctx, Rbac.FINANZAS_VER);
            Long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(obligacionDAO.listarPorIntegrante(id));
        });

        app.get("/api/finanzas/movimientos/totales", ctx -> {
            Rbac.requireAnyPermission(ctx, Rbac.FINANZAS_VER, Rbac.DASHBOARD_VER);
            ctx.json(movimientoDAO.obtenerTotales());
        });

        app.get("/api/finanzas/dashboard/resumen", ctx -> {
            Rbac.requireAnyPermission(ctx, Rbac.FINANZAS_VER, Rbac.DASHBOARD_VER);
            ctx.json(obligacionDAO.obtenerResumenDashboard());
        });

        app.get("/api/finanzas/dashboard/buscar-pendientes", ctx -> {
            Rbac.requireAnyPermission(ctx, Rbac.FINANZAS_VER, Rbac.DASHBOARD_VER);
            String q = ctx.queryParam("q") != null ? ctx.queryParam("q") : "";
            ctx.json(obligacionDAO.buscarDeudores(q));
        });

        app.get("/api/finanzas/dashboard/buscar-obligaciones-integrantes", ctx -> {
            Rbac.requireAnyPermission(ctx, Rbac.FINANZAS_VER, Rbac.DASHBOARD_VER);
            String q = ctx.queryParam("q") != null ? ctx.queryParam("q") : "";
            ctx.json(obligacionDAO.buscarObligacionesIntegrantes(q));
        });

        app.get("/api/finanzas/dashboard/buscar-terceros", ctx -> {
            Rbac.requireAnyPermission(ctx, Rbac.FINANZAS_VER, Rbac.DASHBOARD_VER);
            String q = ctx.queryParam("q") != null ? ctx.queryParam("q") : "";
            ctx.json(obligacionDAO.buscarObligacionesTerceros(q));
        });

        app.get("/api/finanzas/dashboard/ultimos-movimientos", ctx -> {
            Rbac.requireAnyPermission(ctx, Rbac.FINANZAS_VER, Rbac.DASHBOARD_VER);
            ctx.json(movimientoDAO.listarConFiltros(null, null, null));
        });
    }

    private PaymentReceiptData receiptData(
            Long obligacionId,
            BigDecimal monto,
            String method,
            String source,
            String reference,
            String reviewedBy,
            String notes
    ) {
        Map<String, Object> row = obligacionDAO.obtenerDatosRecibo(obligacionId);
        PaymentReceiptData data = new PaymentReceiptData();
        data.receiptNumber = receiptService.newReceiptNumber(source, System.nanoTime());
        data.issuedAt = LocalDateTime.now();
        data.payerName = text(row.get("tercero_nombre"));
        data.documentType = text(row.get("tipo_documento"));
        data.documentNumber = text(row.get("numero_documento"));
        data.contact = join(text(row.get("telefono")), text(row.get("correo")));
        data.concept = text(row.get("concepto_nombre"));
        data.description = notes == null || notes.isBlank() ? text(row.get("obligacion_descripcion")) : notes;
        data.amount = monto;
        data.reference = reference;
        data.method = method;
        data.status = "VALIDADO";
        data.source = source;
        data.reviewedBy = reviewedBy == null || reviewedBy.isBlank() ? "SISTEMA" : reviewedBy;
        data.notes = "Recibo del sistema validado automaticamente";
        return data;
    }

    private StoredPaymentSupport storeSupport(UploadedFile file) {
        validateSupport(file);
        String folder = "documentos/pagos";
        String filename = "comprobante_" + UUID.randomUUID() + extensionFor(file);
        Path targetDir = uploadBasePath.resolve(folder).normalize();
        Path target = targetDir.resolve(filename).normalize();
        if (!target.startsWith(uploadBasePath)) {
            throw new RuntimeException("Ruta de soporte invalida");
        }

        try {
            Files.createDirectories(targetDir);
            try (InputStream input = file.content()) {
                Files.copy(input, target);
            }
            return new StoredPaymentSupport(
                    sanitizeFilename(file.filename()),
                    uploadBasePath.relativize(target).toString().replace('\\', '/')
            );
        } catch (Exception e) {
            throw new RuntimeException("No se pudo guardar el soporte de pago: " + e.getMessage());
        }
    }

    private void validateSupport(UploadedFile file) {
        if (file.size() <= 0 || file.size() > maxSupportFileSizeBytes) {
            throw new RuntimeException("Tamano de soporte invalido");
        }
        String contentType = file.contentType() == null ? "" : file.contentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SUPPORT_MIME_TYPES.contains(contentType)) {
            throw new RuntimeException("Tipo de soporte no permitido");
        }
    }

    private String extensionFor(UploadedFile file) {
        String contentType = file.contentType() == null ? "" : file.contentType().toLowerCase(Locale.ROOT);
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String sanitizeFilename(String filename) {
        String clean = filename == null || filename.isBlank() ? "soporte" : filename.trim();
        return clean.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String join(String left, String right) {
        if (left.isBlank()) return right;
        if (right.isBlank()) return left;
        return left + " / " + right;
    }

    private String text(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    private record StoredPaymentSupport(String originalFilename, String relativePath) {
    }
}
