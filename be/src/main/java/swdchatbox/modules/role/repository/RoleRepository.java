package swdchatbox.modules.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import swdchatbox.modules.role.entity.Role;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID>, JpaSpecificationExecutor<Role> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);
}
