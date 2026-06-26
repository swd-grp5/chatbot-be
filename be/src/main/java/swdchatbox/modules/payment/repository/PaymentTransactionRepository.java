package swdchatbox.modules.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import swdchatbox.modules.payment.entity.PaymentTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByVnpTxnRef(String vnpTxnRef);

    @Query("""
            SELECT p FROM PaymentTransaction p
            JOIN p.invoice i
            LEFT JOIN i.userSubscription us
            LEFT JOIN i.wallet w
            WHERE us.user.id = :userId OR w.user.id = :userId
            ORDER BY p.createdAt DESC
            """)
    List<PaymentTransaction> findAllByInvoiceOwnerUserId(@Param("userId") UUID userId);

    Optional<PaymentTransaction> findByInvoice_Id(UUID invoiceId);
}
