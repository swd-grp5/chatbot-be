package swdchatbox.modules.embedding.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vector_collections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String name; // swd392_documents

    @Column(nullable = false, length = 50)
    private String provider; // QDRANT, PINECONE, PGVECTOR

    @Column(nullable = false, length = 100)
    private String embeddingModel;

    @Column(nullable = false)
    private Integer dimension;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}