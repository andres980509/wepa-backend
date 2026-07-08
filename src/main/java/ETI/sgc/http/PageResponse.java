package ETI.sgc.http;

import java.util.List;

public class PageResponse<T> {
    public final List<T> data;
    public final int page;
    public final int size;
    public final long total;

    public PageResponse(List<T> data, int page, int size, long total) {
        this.data = data;
        this.page = page;
        this.size = size;
        this.total = total;
    }
}
