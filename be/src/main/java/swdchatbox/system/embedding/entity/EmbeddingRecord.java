package swdchatbox.system.embedding.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import swdchatbox.system.document.entity.DocumentChunk;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "embedding_records",
        indexes = {
                @Index(name = "idx_embedding_vector_id", columnList = "vectorId"),
                @Index(name = "idx_embedding_model", columnList = "embeddingModel")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmbeddingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id", nullable = false, unique = true)
    private DocumentChunk chunk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private VectorCollection collection;

    @Column(nullable = false, unique = true, length = 255)
    private String vectorId;

    @Column(nullable = false, length = 100)
    private String embeddingModel;

    @Column(nullable = false)
    private Integer dimension;

    @Column(nullable = false)
    private Boolean embedded = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}