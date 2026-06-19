package swdchatbox.system.subscription.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.system.subscription.entity.UserSubscription;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {

    Optional<UserSubscription> findByUser_IdAndSubscriptionPlan_Id(UUID userId, UUID planId);

    @Query("""
        SELECT s
        FROM UserSubscription s
        WHERE s.user.id = :userId
          AND s.active = true
          AND s.expiresAt > :now
        """)
    Optional<UserSubscription> findActiveSubscription(
            @Param("userId") UUID userId,
            @Param("now") LocalDateTime now
    );

    List<UserSubscription> findAllByUser_IdOrderBySubscribedAtDesc(UUID userId);

    List<UserSubscription> findAllByUser_IdAndActiveOrderBySubscribedAtDesc(UUID userId, Boolean active);

    boolean existsByUser_Id(UUID userId);
}
