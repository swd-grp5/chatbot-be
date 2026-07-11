package swdchatbox.modules.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
@Getter
@Setter
// đây là config cho AI để test model nào ngon
public class AiProperties {

    private String provider = "gemini"; // "gemini" or "openai"

    private String geminiApiKey;
    private String geminiChatModel = "gemini-3.5-flash";
    private String geminiEmbeddingModel = "gemini-embedding-001";

    private String openaiApiKey;
    private String openaiChatModel = "gpt-4o-mini";
    private String openaiEmbeddingModel = "text-embedding-3-small";

    private Double temperature = 0.3;
    private Integer maxTokens = 2048;
    private Integer embeddingDimension = 768; // Gemini default; OpenAI=1536

    private Integer retrievalTopK = 5;
    private Double retrievalScoreThreshold = 0.5;
    private Integer conversationHistoryLimit = 10;

    // Qdrant properties removed — vector storage now uses MySQL (in-memory cosine
    // similarity)
}
