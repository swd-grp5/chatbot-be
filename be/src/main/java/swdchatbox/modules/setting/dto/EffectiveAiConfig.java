package swdchatbox.modules.setting.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Runtime AI config resolved from the active DB model setting (with env fallback).
 */
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

    public boolean isOpenAi() {
        return "openai".equalsIgnoreCase(provider);
    }

    public boolean isGemini() {
        return !isOpenAi();
    }
}
