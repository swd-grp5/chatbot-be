package swdchatbox.modules.ai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final AiProperties aiProperties;

    @Bean("geminiRestClient")
    public RestClient geminiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(120));

        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(factory)
                .build();
    }

    @Bean("openaiRestClient")
    public RestClient openaiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(120));

        return RestClient.builder()
                .baseUrl("https://api.openai.com")
                .defaultHeader("Authorization", "Bearer " + aiProperties.getOpenaiApiKey())
                .requestFactory(factory)
                .build();
    }

    @Bean("qdrantRestClient")
    public RestClient qdrantRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));

        String baseUrl = "http://" + aiProperties.getQdrantHost() + ":" + aiProperties.getQdrantPort();
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
