package swdchatbox.modules.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 500)
    private String storedFileName;

    @Column(nullable = false, length = 1000)
    private String filePath;

    @Column(length = 100)
    private String mimeType;

    private Long fileSize;

    @Column(length = 128)
    private String checksum;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}