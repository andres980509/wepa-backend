package ETI.sgc.document.storage;

import java.nio.file.Path;

public class StoredFile {
    public final Path physicalPath;
    public final String relativePath;
    public final String checksumSha256;

    public StoredFile(Path physicalPath, String relativePath, String checksumSha256) {
        this.physicalPath = physicalPath;
        this.relativePath = relativePath;
        this.checksumSha256 = checksumSha256;
    }
}
