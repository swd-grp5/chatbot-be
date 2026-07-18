package swdchatbox.modules.embedding.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.ai.config.AiProperties;
import swdchatbox.modules.document.entity.DocumentChunk;
import swdchatbox.modules.document.repository.DocumentChunkRepository;
import swdchatbox.modules.embedding.dto.VectorSearchResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * VectorStoreService — MySQL-backed vector storage with in-memory cosine
 * similarity search.
 *
 * Replaces the previous Qdrant implementation. Embeddings are stored as JSON
 * arrays
 * in the {@code DocumentChunk.embeddingJson} column. Similarity search loads
 * candidate
 * chunks from MySQL and ranks them in-memory using cosine similarity.
 *
 * This approach mirrors ChabotEduAI (C#/pgvector) but adapted for Java + MySQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final DocumentChunkRepository documentChunkRepository;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    // ─────────────────────────── Write ───────────────────────────

    /**
     * Upsert a single vector: saves the embedding JSON into the DocumentChunk row.
     * The {@code id} must be the chunk's UUID string.
     */
    @Transactional
    public void upsert(String id, List<Double> vector, Map<String, Object> metadata) {
        saveEmbeddingToChunk(id, vector);
    }

    /**
     * Upsert a batch of vectors.
     */
    @Transactional
    public void upsertBatch(List<PointData> points) {
        for (PointData p : points) {
            saveEmbeddingToChunk(p.id(), p.vector());
        }
    }

    private void saveEmbeddingToChunk(String chunkIdStr, List<Double> vector) {
        try {
            UUID chunkId = UUID.fromString(chunkIdStr);
            Optional<DocumentChunk> opt = documentChunkRepository.findById(chunkId);
            if (opt.isEmpty()) {
                log.warn("saveEmbeddingToChunk: chunk not found id={}", chunkIdStr);
                return;
            }
            DocumentChunk chunk = opt.get();
            chunk.setEmbeddingJson(objectMapper.writeValueAsString(vector));
            documentChunkRepository.save(chunk);
            log.debug("Saved embedding for chunkId={} dim={}", chunkIdStr, vector.size());
        } catch (Exception e) {
            log.error("Failed to save embedding for chunkId={}", chunkIdStr, e);
            throw new RuntimeException("Vector upsert failed", e);
        }
    }

    // ─────────────────────────── Read / Search ───────────────────────────

    /**
     * Similarity search: loads candidate chunks from MySQL, computes cosine
     * similarity
     * in-memory, filters by score threshold, and returns top-K results.
     *
     * @param queryVector embedding of the user's question
     * @param topK        maximum number of results to return
     * @param filter      optional filter map; supports keys:
     *                    {@code "documentId"} (single UUID string) or
     *                    {@code "documentIds"} (List&lt;String&gt;)
     */
    public List<VectorSearchResult> search(List<Double> queryVector, int topK, Map<String, Object> filter) {
        return search(queryVector, topK, filter, aiProperties.getRetrievalScoreThreshold());
    }

    /**
     * Similarity search with an explicit score threshold (use {@code 0.0} to keep top-K
     * even when scores are low — useful for title-scoped / summary questions).
     */
    public List<VectorSearchResult> search(
            List<Double> queryVector,
            int topK,
            Map<String, Object> filter,
            double threshold) {
        try {
            // 1. Load candidate chunks from MySQL
            List<DocumentChunk> candidates = loadCandidates(filter);
            if (candidates.isEmpty()) {
                log.debug("Vector search: no indexed chunks found for filter={}", filter);
                return List.of();
            }

            double[] qArr = toDoubleArray(queryVector);

            // 2. Compute cosine similarity for each candidate in-memory
            List<VectorSearchResult> results = new ArrayList<>();
            for (DocumentChunk chunk : candidates) {
                try {
                    List<Double> embeddingVec = objectMapper.readValue(
                            chunk.getEmbeddingJson(), new TypeReference<List<Double>>() {
                            });
                    double[] cArr = toDoubleArray(embeddingVec);
                    double score = cosineSimilarity(qArr, cArr);

                    if (score >= threshold) {
                        results.add(VectorSearchResult.builder()
                                .id(chunk.getId().toString())
                                .score(score)
                                .metadata(buildMetadata(chunk))
                                .build());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse embedding for chunkId={}", chunk.getId(), e);
                }
            }

            // 3. Sort descending by score, take top-K
            results.sort(Comparator.comparingDouble(VectorSearchResult::getScore).reversed());
            List<VectorSearchResult> topResults = results.stream().limit(topK).collect(Collectors.toList());

            log.debug("Vector search: candidates={} above-threshold={} topK={} threshold={} returned={}",
                    candidates.size(), results.size(), topK, threshold, topResults.size());
            return topResults;

        } catch (Exception e) {
            log.error("Vector search failed", e);
            throw new RuntimeException("Vector search failed", e);
        }
    }

    // ─────────────────────────── Delete ───────────────────────────

    /**
     * Clear embeddings for a list of chunk IDs (sets embeddingJson = null).
     * No external call needed — data lives in MySQL with the chunk.
     */
    @Transactional
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty())
            return;
        for (String idStr : ids) {
            try {
                UUID chunkId = UUID.fromString(idStr);
                documentChunkRepository.findById(chunkId).ifPresent(chunk -> {
                    chunk.setEmbeddingJson(null);
                    documentChunkRepository.save(chunk);
                });
            } catch (IllegalArgumentException ignored) {
                // not a valid UUID — skip
            } catch (Exception e) {
                log.warn("Failed to clear embedding for id={}", idStr, e);
            }
        }
    }

    /**
     * Clear all embeddings associated with a document (by setting embeddingJson =
     * null).
     * The actual chunk rows will be removed by
     * {@code DocumentChunkRepository.deleteAllByDocument_Id()}.
     */
    @Transactional
    public void deleteByDocumentId(UUID documentId) {
        // Chunks themselves are deleted by deleteRelatedDatabaseRecords() in
        // DocumentService.
        // Nothing extra to do for embeddings since they live inside the chunk row.
        log.debug("deleteByDocumentId: embeddings will be removed with chunks for documentId={}", documentId);
    }

    /**
     * No-op: collection management is not needed for MySQL storage.
     */
    public void ensureCollection() {
        log.info("VectorStoreService (MySQL mode): ensureCollection() — no-op");
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    private List<DocumentChunk> loadCandidates(Map<String, Object> filter) {
        // Refuse unscoped search — otherwise chat can pull chunks from unrelated documents.
        if (filter == null || filter.isEmpty()) {
            log.warn("Vector search called with empty filter — returning no results (refusing global scan)");
            return List.of();
        }

        // Single document filter
        if (filter.containsKey("documentId")) {
            String docIdStr = filter.get("documentId").toString();
            try {
                UUID docId = UUID.fromString(docIdStr);
                return documentChunkRepository.findAllWithEmbeddingByDocumentIds(List.of(docId));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid documentId in filter: {}", docIdStr);
                return List.of();
            }
        }

        // Multiple documents filter
        if (filter.containsKey("documentIds")) {
            Object raw = filter.get("documentIds");
            List<UUID> docIds = new ArrayList<>();
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    try {
                        docIds.add(UUID.fromString(item.toString()));
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid UUID in documentIds filter: {}", item);
                    }
                }
            }
            if (docIds.isEmpty())
                return List.of();
            return documentChunkRepository.findAllWithEmbeddingByDocumentIds(docIds);
        }

        // Subject filter
        if (filter.containsKey("subjectId")) {
            String subjectIdStr = filter.get("subjectId").toString();
            try {
                UUID subjectId = UUID.fromString(subjectIdStr);
                return documentChunkRepository.findAllWithEmbeddingBySubjectId(subjectId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid subjectId in filter: {}", subjectIdStr);
                return List.of();
            }
        }

        // Unknown filter key — do not fall back to full scan
        log.warn("Unknown filter keys {}, returning no results", filter.keySet());
        return List.of();
    }

    private Map<String, Object> buildMetadata(DocumentChunk chunk) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("chunkId", chunk.getId().toString());
        if (chunk.getDocument() != null) {
            meta.put("documentId", chunk.getDocument().getId().toString());
            meta.put("documentTitle", chunk.getDocument().getTitle());
        }
        if (chunk.getPageStart() != null)
            meta.put("pageStart", chunk.getPageStart());
        if (chunk.getPageEnd() != null)
            meta.put("pageEnd", chunk.getPageEnd());
        return meta;
    }

    /**
     * Cosine similarity = dot(a, b) / (|a| * |b|).
     * Returns 0.0 if either vector is zero-length.
     */
    private static double cosineSimilarity(double[] a, double[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0)
            return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static double[] toDoubleArray(List<Double> list) {
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++)
            arr[i] = list.get(i);
        return arr;
    }

    /**
     * Record representing a point to upsert (kept for API compatibility).
     */
    public record PointData(String id, List<Double> vector, Map<String, Object> metadata) {
    }
}
