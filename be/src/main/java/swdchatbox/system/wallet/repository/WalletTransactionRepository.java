package swdchatbox.system.wallet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.wallet.entity.WalletTransaction;
import swdchatbox.system.wallet.enums.WalletTransactionType;

import java.util.List;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    List<WalletTransaction> findAllByWallet_User_IdOrderByCreatedAtDesc(UUID userId);
    boolean existsByReferenceIdAndTransactionType(String referenceId, WalletTransactionType transactionType);
}
