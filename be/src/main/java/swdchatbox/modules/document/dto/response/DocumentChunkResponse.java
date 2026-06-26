package swdchatbox.modules.document.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import swdchatbox.modules.document.entity.DocumentChunk;
import swdchatbox.modules.document.enums.ChunkType;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({"id", "documentId", "chunkIndex", "chunkType", "content", "pageStart", "pageEnd", "tokenCount", "startCharIndex", "endCharIndex", "metadataJson", "createdAt"})
public class DocumentChunkResponse {
    private UUID id;
    private UUID documentId;
    private Integer chunkIndex;
    private ChunkType chunkType;
    private String content;
    private Integer pageStart;
    private Integer pageEnd;
    private Integer tokenCount;
    private Integer startCharIndex;
    private Integer endCharIndex;
    private String metadataJson;
    private LocalDateTime createdAt;

    public static DocumentChunkResponse from(DocumentChunk chunk) {
        return DocumentChunkResponse.builder()
                .id(chunk.getId())
                .documentId(chunk.getDocument() != null ? chunk.getDocument().getId() : null)
                .chunkIndex(chunk.getChunkIndex())
                .chunkType(chunk.getChunkType())
                .content(chunk.getContent())
                .pageStart(chunk.getPageStart())
                .pageEnd(chunk.getPageEnd())
                .tokenCount(chunk.getTokenCount())
                .startCharIndex(chunk.getStartCharIndex())
                .endCharIndex(chunk.getEndCharIndex())
                .metadataJson(chunk.getMetadataJson())
                .createdAt(chunk.getCreatedAt())
                .build();
    }
}
