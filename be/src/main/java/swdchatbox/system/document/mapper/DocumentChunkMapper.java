package swdchatbox.system.document.mapper;

import swdchatbox.system.document.dto.response.DocumentChunkResponse;
import swdchatbox.system.document.entity.DocumentChunk;

public final class DocumentChunkMapper {

    private DocumentChunkMapper() {
    }

    public static DocumentChunkResponse toResponse(DocumentChunk chunk) {
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
