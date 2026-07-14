package swdchatbox.modules.subscription.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import swdchatbox.modules.subscription.entity.SubscriptionPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    Optional<SubscriptionPlan> findByNameIgnoreCase(String name);
    List<SubscriptionPlan> findAllByActiveTrue();
}