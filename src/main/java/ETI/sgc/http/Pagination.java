package ETI.sgc.http;

import io.javalin.http.Context;

public class Pagination {
    public final int page;
    public final int size;
    public final int offset;

    private Pagination(int page, int size) {
        this.page = page;
        this.size = size;
        this.offset = (page - 1) * size;
    }

    public static Pagination from(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int size = parseInt(ctx.queryParam("size"), 25);
        page = Math.max(1, page);
        size = Math.max(1, Math.min(size, 100));
        return new Pagination(page, size);
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
