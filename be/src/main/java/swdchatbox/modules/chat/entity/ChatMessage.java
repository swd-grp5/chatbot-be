package swdchatbox.modules.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import swdchatbox.modules.chat.enums.MessageRole;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_message_conversation", columnList = "conversation_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessageRole role;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(length = 100)
    private String llmModel;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String promptUsed;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}