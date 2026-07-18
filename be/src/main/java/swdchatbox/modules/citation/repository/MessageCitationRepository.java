package swdchatbox.modules.citation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.modules.citation.entity.MessageCitation;

import java.util.List;
import java.util.UUID;

public interface MessageCitationRepository extends JpaRepository<MessageCitation, UUID> {

    List<MessageCitation> findAllByMessage_IdOrderByCitationIndexAsc(UUID messageId);

    List<MessageCitation> findAllByMessage_IdIn(List<UUID> messageIds);

    @Query("""
            SELECT c FROM MessageCitation c
            JOIN FETCH c.document
            JOIN FETCH c.chunk
            WHERE c.message.id IN :messageIds
            ORDER BY c.message.id, c.citationIndex ASC
            """)
    List<MessageCitation> findAllByMessageIdInWithRelations(@Param("messageIds") List<UUID> messageIds);

    void deleteAllByMessage_Conversation_Id(UUID conversationId);

    void deleteAllByDocument_Id(UUID documentId);
}
