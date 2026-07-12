package swdchatbox.modules.setting.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.ai.config.AiProperties;
import swdchatbox.modules.setting.entity.ModelSetting;
import swdchatbox.modules.setting.repository.ModelSettingRepository;

@Slf4j
@Component
@Order(25)
@RequiredArgsConstructor
public class DefaultModelSettingInitializer implements CommandLineRunner {

    private final ModelSettingRepository modelSettingRepository;
    private final AiProperties aiProperties;

    @Override
    @Transactional
    public void run(String... args) {
        if (modelSettingRepository.count() > 0) {
            return;
        }

        boolean openai = "openai".equalsIgnoreCase(aiProperties.getProvider());
        ModelSetting setting = ModelSetting.builder()
                .provider(openai ? "openai" : "gemini")
                .chatModel(openai ? aiProperties.getOpenaiChatModel() : aiProperties.getGeminiChatModel())
                .embeddingModel(openai ? aiProperties.getOpenaiEmbeddingModel() : aiProperties.getGeminiEmbeddingModel())
                .temperature(aiProperties.getTemperature())
                .topK(aiProperties.getRetrievalTopK())
                .maxTokens(aiProperties.getMaxTokens())
                .active(true)
                .build();

        modelSettingRepository.save(setting);
        log.info("Seeded default model setting: provider={}, chatModel={}",
                setting.getProvider(), setting.getChatModel());
    }
}
