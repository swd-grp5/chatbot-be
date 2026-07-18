package swdchatbox.modules.setting.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EffectiveAiConfig {

    private final String provider;
    private final String chatModel;
    private final String embeddingModel;
    private final Double temperature;
    private final Integer maxTokens;
    private final Integer topK;
    private final boolean fromDatabase;
    private final boolean apiKeyConfigured;

    @JsonIgnore
    private final String apiKey;

    public boolean isOpenAi() {
        return "openai".equalsIgnoreCase(provider);
    }

    public boolean isGemini() {
        return !isOpenAi();
    }
}
