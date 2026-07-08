package ETI.sgc.payment.transfer;

import ETI.sgc.config.AppConfig;
import ETI.sgc.error.ApiException;
import ETI.sgc.model.Usuario;
import ETI.sgc.payment.receipt.GeneratedReceipt;
import ETI.sgc.payment.receipt.PaymentReceiptData;
import ETI.sgc.payment.receipt.PaymentReceiptService;
import io.javalin.http.UploadedFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PaymentTransferService {
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private final PaymentTransferDAO dao;
    private final Path uploadBasePath;
    private final long maxFileSizeBytes;
    private final PaymentReceiptService receiptService;

    public PaymentTransferService(PaymentTransferDAO dao, AppConfig config) {
        this.dao = dao;
        this.uploadBasePath = Path.of(config.get("UPLOAD_BASE_DIR", "uploads")).toAbsolutePath().normalize();
        this.maxFileSizeBytes = config.getLong("TRANSFER_SUPPORT_MAX_FILE_SIZE_BYTES", 10L * 1024L * 1024L);
        this.receiptService = new PaymentReceiptService(config);
    }

    public Map<String, Object> submit(Usuario user, PaymentTransferRequest request, UploadedFile support) {
        validateUser(user);
        validateRequest(request);

        BigDecimal amount = parseAmount(request.amount);
        Map<String, Object> obligation = dao.findObligationForIntegrante(request.obligacion_id, user.integrante_id);
        if (obligation == null) {
            throw new ApiException(404, "Obligacion no encontrada para el integrante");
        }

        BigDecimal saldo = amountFrom(obligation.get("saldo"));
        if (amount.compareTo(saldo) > 0) {
            throw new ApiException(400, "El monto no puede superar el saldo pendiente");
        }

        String reference = cleanRequired(request.reference, "referencia");
        if (dao.existsActiveReference(user.integrante_id, reference)) {
            throw new ApiException(409, "Ya existe un pago activo con esa referencia");
        }

        StoredTransferSupport stored = storeSupport(support);
        Long id = dao.createPending(
                request.obligacion_id,
                user.id,
                user.integrante_id,
                amount,
                clean(request.method, "Transferencia bancaria"),
                clean(request.bank, ""),
                reference,
                stored
        );
        dao.insertEvent(id, "SUBMIT", user.username, "Soporte enviado para verificacion", null, "PENDING_VERIFICATION");
        return findById(id);
    }

    public Map<String, Object> submitBackoffice(Usuario user, PaymentTransferRequest request, UploadedFile support) {
        validateBackofficeUser(user);
        validateRequest(request);

        BigDecimal amount = parseAmount(request.amount);
        Map<String, Object> obligation = dao.findIntegranteObligation(request.obligacion_id);
        if (obligation == null) {
            throw new ApiException(404, "Obligacion de integrante no encontrada");
        }

        BigDecimal saldo = amountFrom(obligation.get("saldo"));
        if (amount.compareTo(saldo) > 0) {
            throw new ApiException(400, "El monto no puede superar el saldo pendiente");
        }

        Long integranteId = ((Number) obligation.get("tercero_id")).longValue();
        String reference = cleanRequired(request.reference, "referencia");
        if (dao.existsActiveReference(integranteId, reference)) {
            throw new ApiException(409, "Ya existe un pago activo con esa referencia");
        }

        StoredTransferSupport stored = storeSupport(support);
        Long id = dao.createPending(
                request.obligacion_id,
                user.id,
                integranteId,
                amount,
                clean(request.method, "Transferencia bancaria"),
                clean(request.bank, ""),
                reference,
                stored
        );
        dao.insertEvent(id, "SUBMIT_BACKOFFICE", user.username, "Soporte enviado desde plataforma web para verificacion", null, "PENDING_VERIFICATION");
        return findById(id);
    }

    public List<Map<String, Object>> listAdmin(String status, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(1, page);
        return dao.list(status, null, safeSize, (safePage - 1) * safeSize);
    }

    public List<Map<String, Object>> listMine(Usuario user) {
        validateUser(user);
        return dao.list(null, user.integrante_id, 50, 0);
    }

    public Map<String, Object> findById(Long id) {
        Map<String, Object> transfer = dao.findById(id);
        if (transfer == null) {
            throw new ApiException(404, "Pago por transferencia no encontrado");
        }
        return transfer;
    }

    public Map<String, Object> findForUser(Long id, Usuario user) {
        validateUser(user);
        Map<String, Object> transfer = findById(id);
        Long integranteId = ((Number) transfer.get("integrante_id")).longValue();
        if (!integranteId.equals(user.integrante_id)) {
            throw new ApiException(403, "No puede consultar pagos de otro integrante");
        }
        return transfer;
    }

    public Map<String, Object> approve(Long id, String username, String observations) {
        Map<String, Object> result = dao.approve(id, username, observations == null ? "" : observations.trim());
        Map<String, Object> transfer = findById(id);
        String receiptNumber = String.valueOf(result.get("receipt_number"));
        GeneratedReceipt receipt = receiptService.generate(receiptData(transfer, receiptNumber, username, observations));
        dao.attachReceipt(id, receipt.relativePath);
        Long movementId = ((Number) result.get("movement_id")).longValue();
        dao.attachMovementDocuments(id, movementId, receipt.filename, receipt.relativePath);
        return findById(id);
    }

    public Map<String, Object> reject(Long id, String username, String observations) {
        String message = observations == null || observations.isBlank() ? "Pago rechazado por administracion" : observations.trim();
        dao.reject(id, username, message);
        return findById(id);
    }

    public List<Map<String, Object>> history(Long transferId) {
        findById(transferId);
        return dao.history(transferId);
    }

    public Path resolveSupport(Map<String, Object> transfer) {
        return resolveStoredPath(String.valueOf(transfer.get("support_path")));
    }

    public Path resolveReceipt(Map<String, Object> transfer) {
        Object receipt = transfer.get("receipt_path");
        if (receipt == null || String.valueOf(receipt).isBlank() || "null".equals(String.valueOf(receipt))) {
            throw new ApiException(404, "Recibo aun no disponible");
        }
        return resolveStoredPath(String.valueOf(receipt));
    }

    private StoredTransferSupport storeSupport(UploadedFile file) {
        validateFile(file);
        String ext = extensionFor(file);
        String folder = "payment-transfers/supports/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String filename = "support_" + UUID.randomUUID() + ext;
        Path targetDir = uploadBasePath.resolve(folder).normalize();
        Path target = targetDir.resolve(filename).normalize();
        if (!target.startsWith(uploadBasePath)) {
            throw new ApiException(400, "Ruta de soporte invalida");
        }
        try {
            Files.createDirectories(targetDir);
            try (InputStream input = file.content()) {
                Files.copy(input, target);
            }
            return new StoredTransferSupport(
                    uploadBasePath.relativize(target).toString().replace('\\', '/'),
                    sanitizeFilename(file.filename()),
                    file.contentType().toLowerCase(Locale.ROOT),
                    file.size()
            );
        } catch (Exception e) {
            throw new ApiException(500, "No se pudo almacenar el soporte");
        }
    }

    private PaymentReceiptData receiptData(Map<String, Object> transfer, String receiptNumber, String username, String observations) {
        PaymentReceiptData data = new PaymentReceiptData();
        data.receiptNumber = receiptNumber;
        data.issuedAt = LocalDateTime.now();
        data.payerName = text(transfer.get("nombre_completo"));
        data.documentType = text(transfer.get("tipo_documento"));
        data.documentNumber = text(transfer.get("numero_documento"));
        data.contact = join(text(transfer.get("telefono")), text(transfer.get("correo")));
        data.concept = text(transfer.get("concepto_nombre"));
        data.description = text(transfer.get("obligacion_descripcion"));
        data.amount = amountFrom(transfer.get("amount"));
        data.currency = text(transfer.get("currency")).isBlank() ? "COP" : text(transfer.get("currency"));
        data.reference = text(transfer.get("reference"));
        data.method = join(text(transfer.get("method")), text(transfer.get("bank")));
        data.status = "VALIDADO";
        data.source = "TRANSFERENCIA_MOVIL";
        data.reviewedBy = username == null || username.isBlank() ? "SISTEMA" : username;
        data.notes = observations == null || observations.isBlank()
                ? "Transferencia validada desde administracion"
                : observations.trim();
        return data;
    }

    private Path resolveStoredPath(String relativePath) {
        Path file = uploadBasePath.resolve(relativePath).normalize();
        if (!file.startsWith(uploadBasePath) || !Files.exists(file)) {
            throw new ApiException(404, "Archivo no encontrado");
        }
        return file;
    }

    private void validateUser(Usuario user) {
        if (user == null || user.integrante_id == null || !"INTEGRANTE".equalsIgnoreCase(user.rol)) {
            throw new ApiException(403, "La app movil WEPA es solo para integrantes");
        }
    }

    private void validateBackofficeUser(Usuario user) {
        if (user == null || !user.activo) {
            throw new ApiException(401, "Usuario no disponible");
        }
    }

    private void validateRequest(PaymentTransferRequest request) {
        if (request == null || request.obligacion_id == null) {
            throw new ApiException(400, "Obligacion requerida");
        }
        cleanRequired(request.reference, "referencia");
    }

    private void validateFile(UploadedFile file) {
        if (file == null) {
            throw new ApiException(400, "Soporte requerido");
        }
        if (file.size() <= 0 || file.size() > maxFileSizeBytes) {
            throw new ApiException(400, "Tamano de soporte invalido");
        }
        String contentType = file.contentType() == null ? "" : file.contentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new ApiException(400, "Tipo de soporte no permitido");
        }
    }

    private BigDecimal parseAmount(String value) {
        try {
            BigDecimal amount = new BigDecimal(cleanRequired(value, "monto"));
            if (amount.signum() <= 0) {
                throw new ApiException(400, "Monto invalido");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Monto invalido");
        }
    }

    private BigDecimal amountFrom(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String extensionFor(UploadedFile file) {
        String contentType = file.contentType().toLowerCase(Locale.ROOT);
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String cleanRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "Campo requerido: " + field);
        }
        return value.trim();
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
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return "null".equalsIgnoreCase(text) ? "" : text;
    }
}
