package swdchatbox.modules.credit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import swdchatbox.modules.credit.entity.CreditFeatureCost;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditFeatureCostRepository extends JpaRepository<CreditFeatureCost, UUID> {
    Optional<CreditFeatureCost> findByFeatureName(String featureName);
}
