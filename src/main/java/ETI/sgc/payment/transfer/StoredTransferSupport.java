package ETI.sgc.payment.transfer;

public class StoredTransferSupport {
    public final String path;
    public final String originalFilename;
    public final String mimeType;
    public final long sizeBytes;

    public StoredTransferSupport(String path, String originalFilename, String mimeType, long sizeBytes) {
        this.path = path;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }
}
