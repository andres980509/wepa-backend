package ETI.sgc.document.storage;

import ETI.sgc.config.AppConfig;

import java.nio.file.Path;
import java.util.Locale;

public final class StorageProviderFactory {
    private StorageProviderFactory() {
    }

    public static StorageProvider create(AppConfig config) {
        String provider = config.get("DOCUMENT_STORAGE_PROVIDER", "local").toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "minio" -> new MinioStorageProvider();
            case "s3" -> new S3StorageProvider();
            default -> new LocalStorageProvider(Path.of(config.get("UPLOAD_BASE_DIR", "uploads")));
        };
    }
}
