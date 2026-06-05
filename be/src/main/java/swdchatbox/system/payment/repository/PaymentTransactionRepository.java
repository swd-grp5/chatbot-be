package swdchatbox.system.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.payment.entity.PaymentTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByVnpTxnRef(String vnpTxnRef);

    List<PaymentTransaction> findAllByUserEmailOrderByCreatedAtDesc(String userEmail);
}
