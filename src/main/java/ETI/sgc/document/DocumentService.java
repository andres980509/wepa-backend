package ETI.sgc.document;

import ETI.sgc.audit.AuditDAO;
import ETI.sgc.config.AppConfig;
import ETI.sgc.document.storage.StorageProvider;
import ETI.sgc.document.storage.StorageProviderFactory;
import ETI.sgc.document.storage.StoredFile;
import ETI.sgc.error.ApiException;
import ETI.sgc.http.PageResponse;
import ETI.sgc.http.Pagination;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DocumentService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private static final Set<String> ALLOWED_ENTITY_TYPES = Set.of(
            "INTEGRANTE",
            "USUARIO",
            "PROVEEDOR",
            "PATROCINADOR",
            "ENTIDAD",
            "OBLIGACION",
            "MOVIMIENTO",
            "MOVIMIENTO_FINANCIERO"
    );

    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "CEDULA",
            "CONTRATO",
            "EPS",
            "ARL",
            "FACTURA",
            "SOPORTE",
            "RUT",
            "CAMARA_COMERCIO",
            "SOPORTE_EXTERNO",
            "RECIBO_SISTEMA",
            "OTRO"
    );

    private static final Set<String> ALLOWED_STATUS = Set.of(
            "PENDIENTE",
            "VALIDADO",
            "RECHAZADO",
            "VENCIDO"
    );

    private final DocumentDAO documentDAO;
    private final AuditDAO auditDAO;
    private final StorageProvider storageProvider;
    private final long maxFileSizeBytes;

    public DocumentService(DocumentDAO documentDAO, AppConfig config) {
        this(documentDAO, null, config);
    }

    public DocumentService(DocumentDAO documentDAO, AuditDAO auditDAO, AppConfig config) {
        this.documentDAO = documentDAO;
        this.auditDAO = auditDAO;
        this.storageProvider = StorageProviderFactory.create(config);
        this.maxFileSizeBytes = config.getLong("UPLOAD_MAX_FILE_SIZE_BYTES", 10L * 1024 * 1024);
    }

    public DocumentUploadResult upload(
            UploadedFile file,
            String entidadTipo,
            Long entidadId,
            String tipoDocumento,
            String folderPath,
            String uploadedBy
    ) {
        return upload(file, entidadTipo, entidadId, tipoDocumento, folderPath, null, null, uploadedBy, null);
    }

    public DocumentUploadResult upload(
            UploadedFile file,
            String entidadTipo,
            Long entidadId,
            String tipoDocumento,
            String folderPath,
            String fechaVencimiento,
            String observaciones,
            String uploadedBy,
            String ip
    ) {
        String safeEntity = normalizeCode(entidadTipo, null);
        String safeType = normalizeCode(tipoDocumento, "OTRO");
        validate(file, safeEntity, entidadId, safeType);

        String safeFolder = sanitizeFolder(folderPath);
        String dueDate = normalizeDate(fechaVencimiento);

        return documentDAO.withVersionLock(safeEntity, entidadId, safeType, safeFolder, (handle, version) -> {
            String extension = safeExtension(file);
            String storedName = safeType.toLowerCase(Locale.ROOT) + "_" + safeEntity.toLowerCase(Locale.ROOT)
                    + "_" + entidadId + "_v" + version + "_" + UUID.randomUUID() + extension;
            try {
                StoredFile storedFile = storageProvider.store(file, safeFolder, storedName);
                Long id = documentDAO.insert(
                        handle,
                        storedName,
                        storedFile.relativePath,
                        safeType,
                        safeEntity,
                        entidadId,
                        safeFolder,
                        file.filename(),
                        file.contentType(),
                        file.size(),
                        storedFile.checksumSha256,
                        version,
                        uploadedBy,
                        dueDate,
                        observaciones
                );

                audit(uploadedBy, ip, "SUBIDA", "DOCUMENTOS", "documentos", String.valueOf(id), null,
                        Map.of("id", id, "tipo_documento", safeType, "entidad_tipo", safeEntity, "entidad_id", entidadId));

                return new DocumentUploadResult(id, storedName, storedFile.relativePath, file.contentType(), file.size(), version);
            } catch (IOException e) {
                throw new ApiException(500, "No se pudo almacenar el documento");
            }
        });
    }

    public Map<String, Object> findById(Long id) {
        Map<String, Object> document = documentDAO.findById(id);
        if (document == null) {
            throw new ApiException(404, "Documento no encontrado");
        }
        return document;
    }

    public Path resolveFile(Map<String, Object> document) {
        String rutaUrl = String.valueOf(document.get("ruta_url"));
        Path file = storageProvider.resolve(rutaUrl);
        if (!Files.exists(file)) {
            throw new ApiException(404, "Archivo fisico no encontrado");
        }
        return file;
    }

    public List<Map<String, Object>> listByEntity(String entidadTipo, Long entidadId) {
        return documentDAO.listByEntity(normalizeCode(entidadTipo, null), entidadId);
    }

    public PageResponse<Map<String, Object>> search(DocumentSearchFilters filters, Pagination pagination) {
        normalizeFilters(filters);
        return new PageResponse<>(
                documentDAO.search(filters, pagination),
                pagination.page,
                pagination.size,
                documentDAO.count(filters)
        );
    }

    public PageResponse<Map<String, Object>> expired(Pagination pagination) {
        DocumentSearchFilters filters = new DocumentSearchFilters();
        filters.vencimiento = "VENCIDOS";
        return new PageResponse<>(documentDAO.expired(pagination), pagination.page, pagination.size, documentDAO.count(filters));
    }

    public List<Map<String, Object>> missing(String entidadTipo, Long entidadId) {
        String normalized = entidadTipo == null || entidadTipo.isBlank() ? null : normalizeCode(entidadTipo, null);
        return documentDAO.missing(normalized, entidadId);
    }

    public List<Map<String, Object>> listTypes(String entidadTipo) {
        String normalized = entidadTipo == null || entidadTipo.isBlank() ? null : normalizeCode(entidadTipo, null);
        return documentDAO.listTypes(normalized);
    }

    public Map<String, Object> createType(Map<String, Object> payload, String username, String ip) {
        if (payload == null || payload.get("codigo") == null || payload.get("nombre") == null) {
            throw new ApiException(400, "codigo y nombre son obligatorios");
        }
        String codigo = normalizeCode(String.valueOf(payload.get("codigo")), null);
        if (!ALLOWED_DOCUMENT_TYPES.contains(codigo)) {
            throw new ApiException(400, "Tipo documental no soportado por el esquema actual: " + codigo);
        }
        payload.put("codigo", codigo);
        Long id = documentDAO.createType(payload);
        audit(username, ip, "CREAR_TIPO", "DOCUMENTOS", "document_types", String.valueOf(id), null, payload);
        return Map.of("id", id, "codigo", codigo);
    }

    public void updateStatus(Long id, String estado, String observaciones, String username, String ip) {
        String safeStatus = normalizeStatus(estado);
        Map<String, Object> before = findById(id);
        documentDAO.updateStatus(id, safeStatus, observaciones, username, "VALIDADO".equals(safeStatus));
        audit(username, ip, "CAMBIO_ESTADO", "DOCUMENTOS", "documentos", String.valueOf(id), before,
                Map.of("estado", safeStatus, "observaciones", observaciones == null ? "" : observaciones));
    }

    public void validateDocument(Long id, String observaciones, String username, String ip) {
        updateStatus(id, "VALIDADO", observaciones, username, ip);
    }

    public List<Map<String, Object>> versions(Long id) {
        findById(id);
        return documentDAO.versions(id);
    }

    public Map<String, Object> dashboard() {
        return documentDAO.dashboard();
    }

    public void recordAccess(Long id, String action, String username, String ip) {
        audit(username, ip, action, "DOCUMENTOS", "documentos", String.valueOf(id), null, Map.of("id", id));
    }

    private void validate(UploadedFile file, String entidadTipo, Long entidadId, String tipoDocumento) {
        if (file == null) {
            throw new ApiException(400, "Archivo requerido");
        }
        if (file.size() <= 0 || file.size() > maxFileSizeBytes) {
            throw new ApiException(400, "Tamano de archivo invalido");
        }
        if (file.contentType() == null || !ALLOWED_MIME_TYPES.contains(file.contentType().toLowerCase(Locale.ROOT))) {
            throw new ApiException(400, "Tipo de archivo no permitido");
        }
        if (entidadTipo == null || entidadTipo.isBlank() || entidadId == null || tipoDocumento == null || tipoDocumento.isBlank()) {
            throw new ApiException(400, "Metadatos documentales incompletos");
        }
        if (!ALLOWED_ENTITY_TYPES.contains(entidadTipo)) {
            throw new ApiException(400, "Entidad documental no soportada: " + entidadTipo);
        }
        if (!ALLOWED_DOCUMENT_TYPES.contains(tipoDocumento)) {
            throw new ApiException(400, "Tipo documental no soportado: " + tipoDocumento);
        }
    }

    private String safeExtension(UploadedFile file) {
        String extension = file.extension();
        if (extension == null || extension.isBlank()) {
            return switch (file.contentType().toLowerCase(Locale.ROOT)) {
                case "application/pdf" -> ".pdf";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> ".jpg";
            };
        }
        return extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
    }

    private String sanitizeFolder(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            return "general";
        }
        return folderPath
                .replace('\\', '/')
                .replaceAll("\\.\\.", "")
                .replaceAll("[^a-zA-Z0-9/_-]", "-")
                .replaceAll("/+", "/")
                .replaceAll("^/|/$", "");
    }

    private void normalizeFilters(DocumentSearchFilters filters) {
        filters.entidadTipo = filters.entidadTipo == null || filters.entidadTipo.isBlank()
                ? null
                : normalizeCode(filters.entidadTipo, null);
        filters.tipoDocumento = filters.tipoDocumento == null || filters.tipoDocumento.isBlank()
                ? null
                : normalizeCode(filters.tipoDocumento, null);
        filters.estado = filters.estado == null || filters.estado.isBlank()
                ? null
                : normalizeStatus(filters.estado);
        filters.vencimiento = filters.vencimiento == null || filters.vencimiento.isBlank()
                ? null
                : normalizeCode(filters.vencimiento, null);
    }

    private String normalizeCode(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^A-Z0-9_]", "");
    }

    private String normalizeStatus(String estado) {
        String normalized = normalizeCode(estado, null);
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new ApiException(400, "Estado documental invalido");
        }
        return normalized;
    }

    private String normalizeDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (Exception e) {
            throw new ApiException(400, "fecha_vencimiento invalida. Use YYYY-MM-DD");
        }
    }

    private void audit(String username, String ip, String action, String module, String entity, String entityId, Object before, Object after) {
        if (auditDAO == null) {
            return;
        }
        try {
            auditDAO.insert(username, ip, action, module, entity, entityId, toJson(before), toJson(after));
        } catch (Exception ignored) {
            // La auditoria no debe interrumpir la operacion principal.
        }
    }

    private String toJson(Object value) throws IOException {
        if (value == null) {
            return "{}";
        }
        return MAPPER.writeValueAsString(value);
    }
}
