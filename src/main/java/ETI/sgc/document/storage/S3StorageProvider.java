package ETI.sgc.document.storage;

import ETI.sgc.error.ApiException;
import io.javalin.http.UploadedFile;

import java.nio.file.Path;

public class S3StorageProvider implements StorageProvider {
    @Override
    public StoredFile store(UploadedFile file, String folderPath, String storedName) {
        throw new ApiException(501, "Storage S3 preparado por arquitectura, pendiente credenciales y SDK");
    }

    @Override
    public Path resolve(String relativePath) {
        throw new ApiException(501, "Preview local no disponible para Storage S3");
    }
}
