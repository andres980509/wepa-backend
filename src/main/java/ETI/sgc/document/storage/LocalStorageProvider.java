package ETI.sgc.document.storage;

import ETI.sgc.error.ApiException;
import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public class LocalStorageProvider implements StorageProvider {
    private final Path uploadBasePath;

    public LocalStorageProvider(Path uploadBasePath) {
        this.uploadBasePath = uploadBasePath.toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(UploadedFile file, String folderPath, String storedName) throws IOException {
        Path targetDir = uploadBasePath.resolve("documentos").resolve(folderPath).normalize();
        Path targetFile = targetDir.resolve(storedName).normalize();

        if (!targetFile.startsWith(uploadBasePath)) {
            throw new ApiException(400, "Ruta documental invalida");
        }

        Files.createDirectories(targetDir);
        String checksum = copyAndHash(file, targetFile);
        String relativePath = uploadBasePath.relativize(targetFile).toString().replace('\\', '/');
        return new StoredFile(targetFile, relativePath, checksum);
    }

    @Override
    public Path resolve(String relativePath) {
        Path file = uploadBasePath.resolve(relativePath).normalize();
        if (!file.startsWith(uploadBasePath)) {
            throw new ApiException(400, "Ruta documental invalida");
        }
        return file;
    }

    private String copyAndHash(UploadedFile file, Path targetFile) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = file.content();
                 var outputStream = Files.newOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    outputStream.write(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("No se pudo almacenar archivo", e);
        }
    }
}
