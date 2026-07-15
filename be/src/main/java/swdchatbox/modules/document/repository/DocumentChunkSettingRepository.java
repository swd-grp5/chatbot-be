package swdchatbox.modules.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.modules.document.entity.DocumentChunkSetting;

import java.util.Optional;
import java.util.UUID;

public interface DocumentChunkSettingRepository extends JpaRepository<DocumentChunkSetting, UUID> {

    Optional<DocumentChunkSetting> findFirstByOrderByUpdatedAtDesc();
}
