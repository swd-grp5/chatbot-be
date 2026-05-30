package swdchatbox.system.document.service;

import org.springframework.stereotype.Service;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.entity.DocumentChunk;
import swdchatbox.system.document.enums.ChunkType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentChunkingService {

    private static final int DEFAULT_CHUNK_SIZE = 1200;
    private static final int DEFAULT_CHUNK_OVERLAP = 200;

    public List<DocumentChunk> chunk(Document document, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }

        int chunkSize = DEFAULT_CHUNK_SIZE;
        int overlap = Math.min(DEFAULT_CHUNK_OVERLAP, chunkSize / 2);
        int index = 0;
        int start = 0;

        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkSize);
            String chunkContent = content.substring(start, end).trim();
            if (!chunkContent.isEmpty()) {
                chunks.add(DocumentChunk.builder()
                        .document(document)
                        .chunkIndex(index++)
                        .chunkType(ChunkType.TEXT)
                        .content(chunkContent)
                        .tokenCount(estimateTokenCount(chunkContent))
                        .startCharIndex(start)
                        .endCharIndex(end)
                        .metadataJson(toMetadataJson(Map.of(
                                "startCharIndex", start,
                                "endCharIndex", end,
                                "chunkSize", chunkContent.length()
                        )))
                        .build());
            }

            if (end >= content.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }

        return chunks;
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(String.valueOf(value).replace("\"", "\\\"")).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
