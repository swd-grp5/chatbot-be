package swdchatbox.modules.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_index_jobs",
        indexes = {
                @Index(name = "idx_document_index_jobs_status_next_run", columnList = "status,nextRunAt"),
                @Index(name = "idx_document_index_jobs_document", columnList = "document_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentIndexJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentIndexJobStatus status;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private Integer maxRetries;

    private LocalDateTime nextRunAt;

    @Column(length = 1000)
    private String lastError;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
