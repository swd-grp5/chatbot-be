package swdchatbox.system.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.system.menu.entity.MenuGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuGroupRepository extends JpaRepository<MenuGroup, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    List<MenuGroup> findAllByOrderByDisplayOrderAsc();

    @Query("SELECT DISTINCT g FROM MenuGroup g LEFT JOIN FETCH g.items WHERE g.id = :id")
    Optional<MenuGroup> findByIdWithItems(@Param("id") UUID id);

    @Query("SELECT DISTINCT g FROM MenuGroup g LEFT JOIN FETCH g.items ORDER BY g.displayOrder ASC")
    List<MenuGroup> findAllWithItemsOrderByDisplayOrderAsc();
}
