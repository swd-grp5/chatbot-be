package swdchatbox.system.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.role.entity.Role;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);
}
