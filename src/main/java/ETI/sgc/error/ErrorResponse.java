package ETI.sgc.error;

import java.time.OffsetDateTime;

public class ErrorResponse {
    public final String error;
    public final String path;
    public final String timestamp;

    public ErrorResponse(String error, String path) {
        this.error = error;
        this.path = path;
        this.timestamp = OffsetDateTime.now().toString();
    }
}
