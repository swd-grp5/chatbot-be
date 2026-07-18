package swdchatbox.modules.setting.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import swdchatbox.modules.ai.config.AiProperties;
import swdchatbox.modules.setting.dto.EffectiveAiConfig;
import swdchatbox.modules.setting.dto.request.CreateModelSettingRequest;
import swdchatbox.modules.setting.dto.request.UpdateModelSettingRequest;
import swdchatbox.modules.setting.dto.response.ModelSettingResponse;
import swdchatbox.modules.setting.entity.ModelSetting;
import swdchatbox.modules.setting.mapper.ModelSettingMapper;
import swdchatbox.modules.setting.repository.ModelSettingRepository;
import swdchatbox.shared.crypto.SecretEncryptionService;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelSettingService {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("gemini", "openai");

    private final ModelSettingRepository modelSettingRepository;
    private final AiProperties aiProperties;
    private final SecretEncryptionService secretEncryptionService;

    public List<ModelSettingResponse> findAll() {
        return modelSettingRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(ModelSettingMapper::toResponse)
                .toList();
    }

    public ModelSettingResponse findById(UUID id) {
        return ModelSettingMapper.toResponse(findSetting(id));
    }

    public ModelSettingResponse findActive() {
        return modelSettingRepository.findFirstByActiveTrueOrderByUpdatedAtDesc()
                .map(ModelSettingMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No active model setting found"));
    }

    public EffectiveAiConfig resolveEffectiveConfig() {
        return modelSettingRepository.findFirstByActiveTrueOrderByUpdatedAtDesc()
                .map(this::fromEntity)
                .orElseGet(this::fromProperties);
    }

    @Transactional
    public ModelSettingResponse create(CreateModelSettingRequest request) {
        String provider = normalizeProvider(request.getProvider());
        boolean activate = request.getActive() == null || request.getActive();

        if (activate) {
            modelSettingRepository.deactivateAll();
        }

        ModelSetting setting = ModelSetting.builder()
                .provider(provider)
                .chatModel(request.getChatModel().trim())
                .embeddingModel(request.getEmbeddingModel().trim())
                .temperature(request.getTemperature() != null ? request.getTemperature() : aiProperties.getTemperature())
                .topK(request.getTopK() != null ? request.getTopK() : aiProperties.getRetrievalTopK())
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : aiProperties.getMaxTokens())
                .active(activate)
                .build();

        applyApiKey(setting, request.getApiKey(), false);

        return ModelSettingMapper.toResponse(modelSettingRepository.save(setting));
    }

    @Transactional
    public ModelSettingResponse update(UUID id, UpdateModelSettingRequest request) {
        ModelSetting setting = findSetting(id);

        if (request.getProvider() != null) {
            setting.setProvider(normalizeProvider(request.getProvider()));
        }
        if (request.getChatModel() != null) {
            if (request.getChatModel().isBlank()) {
                throw new BadRequestException("Chat model cannot be blank");
            }
            setting.setChatModel(request.getChatModel().trim());
        }
        if (request.getEmbeddingModel() != null) {
            if (request.getEmbeddingModel().isBlank()) {
                throw new BadRequestException("Embedding model cannot be blank");
            }
            setting.setEmbeddingModel(request.getEmbeddingModel().trim());
        }
        if (request.getTemperature() != null) {
            setting.setTemperature(request.getTemperature());
        }
        if (request.getTopK() != null) {
            setting.setTopK(request.getTopK());
        }
        if (request.getMaxTokens() != null) {
            setting.setMaxTokens(request.getMaxTokens());
        }
        if (request.getActive() != null) {
            if (Boolean.TRUE.equals(request.getActive())) {
                modelSettingRepository.deactivateAllExcept(id);
                setting.setActive(true);
            } else {
                setting.setActive(false);
            }
        }

        if (request.getApiKey() != null) {
            applyApiKey(setting, request.getApiKey(), true);
        }

        return ModelSettingMapper.toResponse(modelSettingRepository.save(setting));
    }

    @Transactional
    public ModelSettingResponse activate(UUID id) {
        ModelSetting setting = findSetting(id);
        modelSettingRepository.deactivateAllExcept(id);
        setting.setActive(true);
        return ModelSettingMapper.toResponse(modelSettingRepository.save(setting));
    }

    @Transactional
    public void delete(UUID id) {
        ModelSetting setting = findSetting(id);
        modelSettingRepository.delete(setting);
    }

    private ModelSetting findSetting(UUID id) {
        return modelSettingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Model setting not found"));
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new BadRequestException("Provider is required");
        }
        String normalized = provider.trim().toLowerCase();
        if (!SUPPORTED_PROVIDERS.contains(normalized)) {
            throw new BadRequestException("Unsupported provider: " + provider + ". Use gemini or openai");
        }
        return normalized;
    }

    private void applyApiKey(ModelSetting setting, String apiKey, boolean allowClear) {
        if (apiKey == null) {
            return;
        }
        if (apiKey.isBlank()) {
            if (allowClear) {
                setting.setEncryptedApiKey(null);
                setting.setApiKeyLastFour(null);
            }
            return;
        }
        String trimmed = apiKey.trim();
        setting.setEncryptedApiKey(secretEncryptionService.encrypt(trimmed));
        setting.setApiKeyLastFour(SecretEncryptionService.lastFour(trimmed));
    }

    private EffectiveAiConfig fromEntity(ModelSetting setting) {
        String provider = setting.getProvider().toLowerCase();
        String apiKey = decryptStoredKey(setting);
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = envApiKeyFor(provider);
        }
        log.debug("Using AI config from DB: provider={}, chatModel={}, apiKeyConfigured={}",
                provider, setting.getChatModel(), apiKey != null && !apiKey.isBlank());
        return EffectiveAiConfig.builder()
                .provider(provider)
                .chatModel(setting.getChatModel())
                .embeddingModel(setting.getEmbeddingModel())
                .temperature(setting.getTemperature() != null ? setting.getTemperature() : aiProperties.getTemperature())
                .maxTokens(setting.getMaxTokens() != null ? setting.getMaxTokens() : aiProperties.getMaxTokens())
                .topK(setting.getTopK() != null ? setting.getTopK() : aiProperties.getRetrievalTopK())
                .fromDatabase(true)
                .apiKey(apiKey)
                .apiKeyConfigured(apiKey != null && !apiKey.isBlank())
                .build();
    }

    private EffectiveAiConfig fromProperties() {
        log.debug("No active model setting in DB; falling back to AiProperties");
        boolean openai = "openai".equalsIgnoreCase(aiProperties.getProvider());
        String provider = openai ? "openai" : "gemini";
        String apiKey = envApiKeyFor(provider);
        return EffectiveAiConfig.builder()
                .provider(provider)
                .chatModel(openai ? aiProperties.getOpenaiChatModel() : aiProperties.getGeminiChatModel())
                .embeddingModel(openai ? aiProperties.getOpenaiEmbeddingModel() : aiProperties.getGeminiEmbeddingModel())
                .temperature(aiProperties.getTemperature())
                .maxTokens(aiProperties.getMaxTokens())
                .topK(aiProperties.getRetrievalTopK())
                .fromDatabase(false)
                .apiKey(apiKey)
                .apiKeyConfigured(apiKey != null && !apiKey.isBlank())
                .build();
    }

    private String decryptStoredKey(ModelSetting setting) {
        if (setting.getEncryptedApiKey() == null || setting.getEncryptedApiKey().isBlank()) {
            return null;
        }
        return secretEncryptionService.decrypt(setting.getEncryptedApiKey());
    }

    private String envApiKeyFor(String provider) {
        if ("openai".equalsIgnoreCase(provider)) {
            return blankToNull(aiProperties.getOpenaiApiKey());
        }
        return blankToNull(aiProperties.getGeminiApiKey());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
