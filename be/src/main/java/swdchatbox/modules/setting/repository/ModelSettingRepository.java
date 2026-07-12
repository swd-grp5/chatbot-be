package swdchatbox.modules.setting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import swdchatbox.modules.setting.entity.ModelSetting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelSettingRepository extends JpaRepository<ModelSetting, UUID> {

    Optional<ModelSetting> findFirstByActiveTrueOrderByUpdatedAtDesc();

    List<ModelSetting> findAllByOrderByUpdatedAtDesc();

    @Modifying
    @Query("UPDATE ModelSetting m SET m.active = false WHERE m.active = true")
    void deactivateAll();

    @Modifying
    @Query("UPDATE ModelSetting m SET m.active = false WHERE m.active = true AND m.id <> :id")
    void deactivateAllExcept(UUID id);
}
