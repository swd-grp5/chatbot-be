package swdchatbox.system.citation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.citation.entity.MessageCitation;

import java.util.List;
import java.util.UUID;

public interface MessageCitationRepository extends JpaRepository<MessageCitation, UUID> {

    List<MessageCitation> findAllByMessage_IdOrderByCitationIndexAsc(UUID messageId);

    List<MessageCitation> findAllByMessage_IdIn(List<UUID> messageIds);

    void deleteAllByMessage_Conversation_Id(UUID conversationId);

    void deleteAllByDocument_Id(UUID documentId);
}
