package swdchatbox.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import swdchatbox.modules.ai.util.AiApiErrorSupport;
import swdchatbox.modules.setting.dto.EffectiveAiConfig;
import swdchatbox.modules.setting.service.ModelSettingService;
import swdchatbox.shared.exception.BadRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private final ModelSettingService modelSettingService;
    private final RestClient geminiRestClient;
    private final RestClient openaiRestClient;
    private final ObjectMapper objectMapper;

    public EmbeddingService(
            ModelSettingService modelSettingService,
            @Qualifier("geminiRestClient") RestClient geminiRestClient,
            @Qualifier("openaiRestClient") RestClient openaiRestClient,
            ObjectMapper objectMapper) {
        this.modelSettingService = modelSettingService;
        this.geminiRestClient = geminiRestClient;
        this.openaiRestClient = openaiRestClient;
        this.objectMapper = objectMapper;
    }

    public List<Double> embed(String text) {
        EffectiveAiConfig config = modelSettingService.resolveEffectiveConfig();
        requireApiKey(config);
        if (config.isOpenAi()) {
            return embedWithOpenAI(text, config.getEmbeddingModel(), config.getApiKey());
        }
        return embedWithGemini(text, config.getEmbeddingModel(), config.getApiKey());
    }

    public List<List<Double>> embedBatch(List<String> texts) {
        EffectiveAiConfig config = modelSettingService.resolveEffectiveConfig();
        requireApiKey(config);
        if (config.isOpenAi()) {
            return embedBatchWithOpenAI(texts, config.getEmbeddingModel(), config.getApiKey());
        }
        return embedBatchWithGemini(texts, config.getEmbeddingModel(), config.getApiKey());
    }

    private List<Double> embedWithGemini(String text, String model, String apiKey) {
        String endpoint = "/v1beta/models/" + model + ":embedContent";
        String url = endpoint + "?key=" + apiKey;

        Map<String, Object> body = Map.of(
                "model", "models/" + model,
                "content", Map.of("parts", List.of(Map.of("text", text))));

        try {
            String response = geminiRestClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode values = root.path("embedding").path("values");
            List<Double> embedding = new ArrayList<>();
            for (JsonNode val : values) {
                embedding.add(val.asDouble());
            }
            return embedding;
        } catch (Exception e) {
            AiApiErrorSupport.logFailure(log, "embedding.single", "gemini", model, endpoint, e);
            throw AiApiErrorSupport.wrap("embedding.single", "gemini", model, endpoint, e);
        }
    }

    private List<List<Double>> embedBatchWithGemini(List<String> texts, String model, String apiKey) {
        String endpoint = "/v1beta/models/" + model + ":batchEmbedContents";
        String url = endpoint + "?key=" + apiKey;

        List<Map<String, Object>> requests = texts.stream()
                .map(text -> Map.<String, Object>of(
                        "model", "models/" + model,
                        "content", Map.of("parts", List.of(Map.of("text", text)))))
                .toList();

        Map<String, Object> body = Map.of("requests", requests);

        try {
            String response = geminiRestClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddings = root.path("embeddings");

            List<List<Double>> result = new ArrayList<>();
            for (JsonNode emb : embeddings) {
                List<Double> vector = new ArrayList<>();
                for (JsonNode val : emb.path("values")) {
                    vector.add(val.asDouble());
                }
                result.add(vector);
            }
            return result;
        } catch (Exception e) {
            AiApiErrorSupport.logFailure(log, "embedding.batch(count=" + texts.size() + ")", "gemini", model, endpoint, e);
            throw AiApiErrorSupport.wrap("embedding.batch(count=" + texts.size() + ")", "gemini", model, endpoint, e);
        }
    }

    private List<Double> embedWithOpenAI(String text, String model, String apiKey) {
        return embedBatchWithOpenAI(List.of(text), model, apiKey).get(0);
    }

    private List<List<Double>> embedBatchWithOpenAI(List<String> texts, String model, String apiKey) {
        String endpoint = "/v1/embeddings";
        Map<String, Object> body = Map.of(
                "model", model,
                "input", texts);

        try {
            String response = openaiRestClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            List<List<Double>> result = new ArrayList<>();
            for (JsonNode item : data) {
                List<Double> vector = new ArrayList<>();
                for (JsonNode val : item.path("embedding")) {
                    vector.add(val.asDouble());
                }
                result.add(vector);
            }
            return result;
        } catch (Exception e) {
            AiApiErrorSupport.logFailure(log, "embedding.batch(count=" + texts.size() + ")", "openai", model, endpoint, e);
            throw AiApiErrorSupport.wrap("embedding.batch(count=" + texts.size() + ")", "openai", model, endpoint, e);
        }
    }

    private static void requireApiKey(EffectiveAiConfig config) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BadRequestException(
                    "AI API key is not configured. Admin can set it via /model-settings or env (GEMINI_API_KEY / OPENAI_API_KEY).");
        }
    }
}
