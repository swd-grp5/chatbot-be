package swdchatbox.modules.chat.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.modules.chat.entity.ChatMessage;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findAllByConversation_IdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    List<ChatMessage> findAllByConversation_IdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * Get the last N messages for building conversation history context.
     */
    List<ChatMessage> findTop20ByConversation_IdOrderByCreatedAtDesc(UUID conversationId);

    long countByConversation_Id(UUID conversationId);

    void deleteAllByConversation_Id(UUID conversationId);
}
