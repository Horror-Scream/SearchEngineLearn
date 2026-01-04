package searchengine.dto.search;

import lombok.Data;
import lombok.NoArgsConstructor;
import searchengine.dto.statistics.ResultResponse;

import java.util.List;

@Data
@NoArgsConstructor
public class SearchResponse extends ResultResponse {
    private int count;
    private List<SearchResult> data;

    public SearchResponse(boolean result) {
        super(result);
    }

    public SearchResponse(boolean result, String error) {
        super(result, error);
    }

    @Data
    @NoArgsConstructor
    public static class SearchResult {
        private String site;
        private String siteName;
        private String uri;
        private String title;
        private String snippet;
        private float relevance;

        public SearchResult(String site, String siteName, String uri, String title, String snippet, float relevance) {
            this.site = site;
            this.siteName = siteName;
            this.uri = uri;
            this.title = title;
            this.snippet = snippet;
            this.relevance = relevance;
        }
    }
}