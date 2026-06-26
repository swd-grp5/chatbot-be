package swdchatbox.modules.citation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import swdchatbox.modules.chat.entity.ChatMessage;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.document.entity.DocumentChunk;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "message_citations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id", nullable = false)
    private DocumentChunk chunk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    private Integer citationIndex;

    private Double score;

    private Integer pageStart;

    private Integer pageEnd;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String quotedText;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}