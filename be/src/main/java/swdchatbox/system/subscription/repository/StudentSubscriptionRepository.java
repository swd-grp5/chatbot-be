package swdchatbox.system.subscription.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.subscription.entity.StudentSubscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentSubscriptionRepository extends JpaRepository<StudentSubscription, UUID> {

    Optional<StudentSubscription> findByStudent_IdAndSubject_Id(UUID studentId, UUID subjectId);

    List<StudentSubscription> findAllByStudent_IdOrderBySubscribedAtDesc(UUID studentId);

    List<StudentSubscription> findAllByStudent_IdAndActiveOrderBySubscribedAtDesc(UUID studentId, Boolean active);
}

