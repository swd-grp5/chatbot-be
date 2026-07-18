package swdchatbox.modules.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.modules.document.entity.DocumentIndexJob;
import swdchatbox.modules.document.entity.DocumentIndexJobStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentIndexJobRepository extends JpaRepository<DocumentIndexJob, UUID> {
    Optional<DocumentIndexJob> findByDocument_Id(UUID documentId);

    void deleteAllByDocument_Id(UUID documentId);

    List<DocumentIndexJob> findTop50ByStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
            List<DocumentIndexJobStatus> statuses,
            LocalDateTime now
    );

    /**
     * Atomically "claim" a job for processing. The status is only flipped to
     * {@code newStatus} when it is still in one of the {@code claimable} states,
     * so that a job can never be picked up by two concurrent runs at once.
     *
     * @return number of rows updated (1 = claimed successfully, 0 = already taken)
     */
    @Modifying(clearAutomatically = true)
    @Query("update DocumentIndexJob j set j.status = :newStatus "
            + "where j.id = :id and j.status in :claimable")
    int claimForProcessing(@Param("id") UUID id,
                           @Param("newStatus") DocumentIndexJobStatus newStatus,
                           @Param("claimable") Collection<DocumentIndexJobStatus> claimable);
}
