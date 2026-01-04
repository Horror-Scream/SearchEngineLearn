package searchengine.dto.statistics;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResultResponse {
    private boolean result;
    private String error;

    public ResultResponse(boolean result) {
        this.result = result;
    }

    public ResultResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }
}