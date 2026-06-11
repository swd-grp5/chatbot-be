package swdchatbox.system.embedding.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import swdchatbox.system.ai.config.AiProperties;
import swdchatbox.system.embedding.dto.VectorSearchResult;

import java.util.*;

/**
 * Service for interacting with Qdrant vector database.
 * Handles collection creation, upserting vectors, and similarity search.
 */
@Slf4j
@Service
public class VectorStoreService {

    private final RestClient qdrantRestClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public VectorStoreService(
            @Qualifier("qdrantRestClient") RestClient qdrantRestClient,
            AiProperties aiProperties,
            ObjectMapper objectMapper) {
        this.qdrantRestClient = qdrantRestClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Ensure the Qdrant collection exists. Creates it if not present.
     */
    public void ensureCollection() {
        String collection = aiProperties.getQdrantCollectionName();
        try {
            qdrantRestClient.get()
                    .uri("/collections/{name}", collection)
                    .retrieve()
                    .body(String.class);
            log.info("Qdrant collection '{}' already exists", collection);
        } catch (Exception e) {
            log.info("Creating Qdrant collection '{}'", collection);
            createCollection(collection);
        }
    }

    private void createCollection(String name) {
        Map<String, Object> body = Map.of(
                "vectors", Map.of(
                        "size", aiProperties.getEmbeddingDimension(),
                        "distance", "Cosine"));

        try {
            qdrantRestClient.put()
                    .uri("/collections/{name}", name)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("Qdrant collection '{}' created with dimension {}", name, aiProperties.getEmbeddingDimension());
        } catch (Exception e) {
            log.error("Failed to create Qdrant collection '{}'", name, e);
            throw new RuntimeException("Failed to create vector collection", e);
        }
    }

    /**
     * Upsert a single vector with metadata.
     */
    public void upsert(String id, List<Double> vector, Map<String, Object> metadata) {
        upsertBatch(List.of(new PointData(id, vector, metadata)));
    }

    /**
     * Upsert a batch of vectors.
     */
    public void upsertBatch(List<PointData> points) {
        String collection = aiProperties.getQdrantCollectionName();

        List<Map<String, Object>> qdrantPoints = points.stream()
                .map(p -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("id", p.id());
                    point.put("vector", p.vector());
                    if (p.metadata() != null && !p.metadata().isEmpty()) {
                        point.put("payload", p.metadata());
                    }
                    return point;
                })
                .toList();

        Map<String, Object> body = Map.of("points", qdrantPoints);

        try {
            qdrantRestClient.put()
                    .uri("/collections/{name}/points", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.debug("Upserted {} vectors to collection '{}'", points.size(), collection);
        } catch (Exception e) {
            log.error("Failed to upsert {} vectors", points.size(), e);
            throw new RuntimeException("Vector upsert failed", e);
        }
    }

    /**
     * Perform similarity search. Returns top-K results with score and metadata.
     */
    public List<VectorSearchResult> search(List<Double> queryVector, int topK, Map<String, Object> filter) {
        String collection = aiProperties.getQdrantCollectionName();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", queryVector);
        body.put("limit", topK);
        body.put("with_payload", true);
        body.put("score_threshold", aiProperties.getRetrievalScoreThreshold());

        if (filter != null && !filter.isEmpty()) {
            // Qdrant filter format: {"must": [{"key": "field", "match": {"value": val}}]}
            List<Map<String, Object>> mustClauses = new ArrayList<>();
            for (Map.Entry<String, Object> entry : filter.entrySet()) {
                mustClauses.add(Map.of(
                        "key", entry.getKey(),
                        "match", Map.of("value", entry.getValue())));
            }
            body.put("filter", Map.of("must", mustClauses));
        }

        try {
            String response = qdrantRestClient.post()
                    .uri("/collections/{name}/points/search", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.path("result");

            List<VectorSearchResult> searchResults = new ArrayList<>();
            for (JsonNode hit : results) {
                String id = hit.path("id").asText();
                double score = hit.path("score").asDouble();

                Map<String, Object> payload = new LinkedHashMap<>();
                JsonNode payloadNode = hit.path("payload");
                if (payloadNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = payloadNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        JsonNode valueNode = field.getValue();
                        if (valueNode.isTextual()) {
                            payload.put(field.getKey(), valueNode.asText());
                        } else if (valueNode.isInt()) {
                            payload.put(field.getKey(), valueNode.asInt());
                        } else if (valueNode.isDouble()) {
                            payload.put(field.getKey(), valueNode.asDouble());
                        } else {
                            payload.put(field.getKey(), valueNode.toString());
                        }
                    }
                }

                searchResults.add(VectorSearchResult.builder()
                        .id(id)
                        .score(score)
                        .metadata(payload)
                        .build());
            }

            return searchResults;
        } catch (Exception e) {
            log.error("Vector search failed", e);
            throw new RuntimeException("Vector search failed", e);
        }
    }

    /**
     * Delete vectors by their IDs.
     */
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String collection = aiProperties.getQdrantCollectionName();
        Map<String, Object> body = Map.of("points", ids);

        try {
            qdrantRestClient.post()
                    .uri("/collections/{name}/points/delete", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.debug("Deleted {} vectors from collection '{}'", ids.size(), collection);
        } catch (Exception e) {
            log.error("Failed to delete vectors", e);
        }
    }

    /**
     * Delete all vectors whose payload matches the given document ID.
     */
    public void deleteByDocumentId(UUID documentId) {
        if (documentId == null) {
            return;
        }
        String collection = aiProperties.getQdrantCollectionName();
        Map<String, Object> body = Map.of(
                "filter", Map.of(
                        "must", List.of(Map.of(
                                "key", "documentId",
                                "match", Map.of("value", documentId.toString())))));

        try {
            qdrantRestClient.post()
                    .uri("/collections/{name}/points/delete", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("Deleted vectors for documentId={} from collection '{}'", documentId, collection);
        } catch (Exception e) {
            log.error("Failed to delete vectors for documentId={}", documentId, e);
        }
    }

    /**
     * Record representing a point to upsert.
     */
    public record PointData(String id, List<Double> vector, Map<String, Object> metadata) {
    }
}
