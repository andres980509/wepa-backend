package ETI.sgc.document.storage;

import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.nio.file.Path;

public interface StorageProvider {
    StoredFile store(UploadedFile file, String folderPath, String storedName) throws IOException;

    Path resolve(String relativePath);
}
