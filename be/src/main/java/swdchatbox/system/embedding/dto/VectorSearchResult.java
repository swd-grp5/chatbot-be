package swdchatbox.system.embedding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResult {

    private String id;
    private Double score;
    private Map<String, Object> metadata;
}
