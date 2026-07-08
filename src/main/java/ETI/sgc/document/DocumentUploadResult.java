package ETI.sgc.document;

public class DocumentUploadResult {
    public final Long id;
    public final String nombre_archivo;
    public final String ruta_url;
    public final String mime_type;
    public final long file_size_bytes;
    public final int version;

    public DocumentUploadResult(Long id, String nombreArchivo, String rutaUrl, String mimeType, long fileSizeBytes, int version) {
        this.id = id;
        this.nombre_archivo = nombreArchivo;
        this.ruta_url = rutaUrl;
        this.mime_type = mimeType;
        this.file_size_bytes = fileSizeBytes;
        this.version = version;
    }
}
