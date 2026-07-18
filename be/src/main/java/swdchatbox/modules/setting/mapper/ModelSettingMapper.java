package swdchatbox.modules.setting.mapper;

import swdchatbox.modules.setting.dto.response.ModelSettingResponse;
import swdchatbox.modules.setting.entity.ModelSetting;

public final class ModelSettingMapper {

    private ModelSettingMapper() {
    }

    public static ModelSettingResponse toResponse(ModelSetting setting) {
        if (setting == null) {
            return null;
        }
        boolean hasKey = setting.getEncryptedApiKey() != null && !setting.getEncryptedApiKey().isBlank();
        String masked = null;
        if (hasKey && setting.getApiKeyLastFour() != null) {
            masked = "****" + setting.getApiKeyLastFour();
        } else if (hasKey) {
            masked = "****";
        }
        return ModelSettingResponse.builder()
                .id(setting.getId())
                .provider(setting.getProvider())
                .chatModel(setting.getChatModel())
                .embeddingModel(setting.getEmbeddingModel())
                .hasApiKey(hasKey)
                .apiKeyMasked(masked)
                .temperature(setting.getTemperature())
                .topK(setting.getTopK())
                .maxTokens(setting.getMaxTokens())
                .active(setting.getActive())
                .createdAt(setting.getCreatedAt())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }
}
