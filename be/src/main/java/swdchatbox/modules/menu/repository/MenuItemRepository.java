package swdchatbox.modules.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.modules.menu.entity.MenuItem;

import java.util.List;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    List<MenuItem> findByMenuGroup_IdOrderByDisplayOrderAsc(UUID menuGroupId);

    boolean existsByMenuGroup_IdAndTitleIgnoreCase(UUID menuGroupId, String title);

    boolean existsByMenuGroup_IdAndTitleIgnoreCaseAndIdNot(UUID menuGroupId, String title, UUID id);

    boolean existsByUrlIgnoreCase(String url);

    boolean existsByUrlIgnoreCaseAndIdNot(String url, UUID id);
}
