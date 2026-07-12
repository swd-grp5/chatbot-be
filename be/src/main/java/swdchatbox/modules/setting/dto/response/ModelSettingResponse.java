package swdchatbox.modules.setting.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({
        "id", "provider", "chatModel", "embeddingModel",
        "hasApiKey", "apiKeyMasked",
        "temperature", "topK", "maxTokens", "active",
        "createdAt", "updatedAt"
})
public class ModelSettingResponse {

    private UUID id;
    private String provider;
    private String chatModel;
    private String embeddingModel;
    private boolean hasApiKey;
    private String apiKeyMasked;
    private Double temperature;
    private Integer topK;
    private Integer maxTokens;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
