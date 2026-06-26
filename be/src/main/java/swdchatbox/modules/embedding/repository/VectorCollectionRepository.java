package swdchatbox.modules.embedding.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.modules.embedding.entity.VectorCollection;

import java.util.Optional;
import java.util.UUID;

public interface VectorCollectionRepository extends JpaRepository<VectorCollection, UUID> {

    Optional<VectorCollection> findByName(String name);

    Optional<VectorCollection> findByNameAndActiveTrue(String name);
}
