package swdchatbox.system.subscription.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.system.subscription.entity.StudentSubscription;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentSubscriptionRepository extends JpaRepository<StudentSubscription, UUID> {

    Optional<StudentSubscription> findByStudent_IdAndSubscriptionPlan_Id(
            UUID studentId,
            UUID planId
    );


    @Query("""
        SELECT s
        FROM StudentSubscription s
        WHERE s.student.id = :studentId
          AND s.active = true
          AND s.expiresAt > :now
        """)
    Optional<StudentSubscription> findActiveSubscription(
            @Param("studentId") UUID studentId,
            @Param("now") LocalDateTime now
    );

    List<StudentSubscription> findAllByStudent_IdOrderBySubscribedAtDesc(UUID studentId);

    List<StudentSubscription> findAllByStudent_IdAndActiveOrderBySubscribedAtDesc(
            UUID studentId,
            Boolean active
    );

    boolean existsByStudent_Id(UUID studentId);
}