package swdchatbox.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import swdchatbox.modules.ai.config.AiProperties;
import swdchatbox.modules.ai.dto.LlmMessage;
import swdchatbox.modules.ai.dto.LlmResponse;

import java.util.*;

/**
 * Service for calling LLM chat completion APIs (Gemini or OpenAI).
 * Used in the RAG pipeline to generate answers from retrieved context.
 */
@Slf4j
@Service
public class LlmService {

    private final AiProperties aiProperties;
    private final RestClient geminiRestClient;
    private final RestClient openaiRestClient;
    private final ObjectMapper objectMapper;

    public LlmService(
            AiProperties aiProperties,
            @Qualifier("geminiRestClient") RestClient geminiRestClient,
            @Qualifier("openaiRestClient") RestClient openaiRestClient,
            ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.geminiRestClient = geminiRestClient;
        this.openaiRestClient = openaiRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a response from the LLM given a list of messages.
     */
    public LlmResponse generate(List<LlmMessage> messages) {
        return generate(messages, aiProperties.getTemperature(), aiProperties.getMaxTokens());
    }

    /**
     * Generate a response with custom temperature and max tokens.
     */
    public LlmResponse generate(List<LlmMessage> messages, double temperature, int maxTokens) {
        if ("openai".equalsIgnoreCase(aiProperties.getProvider())) {
            return generateWithOpenAI(messages, temperature, maxTokens);
        }
        return generateWithGemini(messages, temperature, maxTokens);
    }

    // ──────────────────────── Gemini ────────────────────────

    private LlmResponse generateWithGemini(List<LlmMessage> messages, double temperature, int maxTokens) {
        String model = aiProperties.getGeminiChatModel();
        String url = "/v1beta/models/" + model + ":generateContent?key=" + aiProperties.getGeminiApiKey();

        // Gemini uses "contents" array with role: "user" / "model"
        // System instruction is separate
        List<Map<String, Object>> contents = new ArrayList<>();
        String systemInstruction = null;

        for (LlmMessage msg : messages) {
            if ("system".equals(msg.getRole())) {
                // Gemini treats system instructions separately
                systemInstruction = (systemInstruction == null ? "" : systemInstruction + "\n\n") + msg.getContent();
            } else {
                String geminiRole = "assistant".equals(msg.getRole()) ? "model" : "user";
                contents.add(Map.of(
                        "role", geminiRole,
                        "parts", List.of(Map.of("text", msg.getContent()))));
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (systemInstruction != null) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemInstruction))));
        }
        body.put("contents", contents);
        body.put("generationConfig", Map.of(
                "temperature", temperature,
                "maxOutputTokens", maxTokens,
                "topP", 0.95));

        try {
            String response = geminiRestClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            JsonNode usage = root.path("usageMetadata");
            int promptTokens = usage.path("promptTokenCount").asInt(0);
            int completionTokens = usage.path("candidatesTokenCount").asInt(0);

            return LlmResponse.builder()
                    .content(content)
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .build();
        } catch (Exception e) {
            log.error("Gemini LLM call failed", e);
            throw new RuntimeException("LLM API call failed", e);
        }
    }

    // ──────────────────────── OpenAI ────────────────────────

    private LlmResponse generateWithOpenAI(List<LlmMessage> messages, double temperature, int maxTokens) {
        String model = aiProperties.getOpenaiChatModel();

        List<Map<String, String>> openaiMessages = messages.stream()
                .map(msg -> Map.of("role", msg.getRole(), "content", msg.getContent()))
                .toList();

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", openaiMessages,
                "temperature", temperature,
                "max_tokens", maxTokens);

        try {
            String response = openaiRestClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();

            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);

            return LlmResponse.builder()
                    .content(content)
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .build();
        } catch (Exception e) {
            log.error("OpenAI LLM call failed", e);
            throw new RuntimeException("LLM API call failed", e);
        }
    }
}
