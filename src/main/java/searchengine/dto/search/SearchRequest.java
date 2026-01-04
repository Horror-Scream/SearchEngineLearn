package searchengine.dto.search;

import lombok.Data;

@Data
public class SearchRequest {
    private final String query;
    private final String site;
    private final int offset;
    private final int limit;

    public SearchRequest(String query, String site, int offset, int limit) {
        this.query = query != null ? query.trim() : null;
        this.site = site;
        this.offset = offset;
        this.limit = limit;
    }
}