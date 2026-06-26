package swdchatbox.modules.subscription.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import swdchatbox.modules.subscription.entity.SubscriptionPlan;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    // Tìm kiếm gói mặc định theo tên (không phân biệt chữ hoa chữ thường)
    Optional<SubscriptionPlan> findByNameIgnoreCase(String name);
}