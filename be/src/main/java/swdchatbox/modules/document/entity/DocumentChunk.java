package swdchatbox.modules.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import swdchatbox.modules.document.enums.ChunkType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_chunks", indexes = {
                @Index(name = "idx_document_chunk_document", columnList = "document_id"),
                @Index(name = "idx_document_chunk_index", columnList = "chunkIndex")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "document_id", nullable = false)
        private Document document;

        @Column(nullable = false)
        private Integer chunkIndex;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 30)
        private ChunkType chunkType;

        @Lob
        @Column(nullable = false, columnDefinition = "LONGTEXT")
        private String content;

        private Integer pageStart;

        private Integer pageEnd;

        private Integer tokenCount;

        private Integer startCharIndex;

        private Integer endCharIndex;

        @Lob
        @Column(columnDefinition = "TEXT")
        private String metadataJson;

        /**
         * Embedding vector stored as JSON array, e.g. [0.1, 0.2, ...].
         * Used for in-memory cosine similarity search (replaces Qdrant).
         */
        @Lob
        @Column(columnDefinition = "MEDIUMTEXT")
        private String embeddingJson;

        @CreationTimestamp
        @Column(updatable = false)
        private LocalDateTime createdAt;
}