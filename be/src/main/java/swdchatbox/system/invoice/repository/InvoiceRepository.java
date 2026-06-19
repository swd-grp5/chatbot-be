package swdchatbox.system.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.system.invoice.entity.Invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("""
            SELECT i FROM Invoice i
            LEFT JOIN i.userSubscription us
            LEFT JOIN i.wallet w
            WHERE us.user.id = :userId OR w.user.id = :userId
            ORDER BY i.issuedAt DESC
            """)
    List<Invoice> findAllByOwnerUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT i FROM Invoice i
            LEFT JOIN i.userSubscription us
            LEFT JOIN i.wallet w
            WHERE i.id = :id AND (us.user.id = :userId OR w.user.id = :userId)
            """)
    Optional<Invoice> findByIdAndOwnerUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
