package swdchatbox.modules.chat.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.modules.chat.entity.ChatConversation;

import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    Page<ChatConversation> findAllByUser_IdAndActiveTrueOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    // Chỉ tìm conversation còn active (chưa bị soft-delete)
    Optional<ChatConversation> findByIdAndUser_IdAndActiveTrue(UUID id, UUID userId);

    long countByUser_Id(UUID userId);
}
