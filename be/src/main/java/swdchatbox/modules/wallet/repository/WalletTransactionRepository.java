package swdchatbox.modules.wallet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import swdchatbox.modules.wallet.entity.WalletTransaction;
import swdchatbox.modules.wallet.enums.WalletTransactionType;

import java.util.List;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID>, JpaSpecificationExecutor<WalletTransaction> {
    List<WalletTransaction> findAllByWallet_User_IdOrderByCreatedAtDesc(UUID userId);
    boolean existsByReferenceIdAndTransactionType(String referenceId, WalletTransactionType transactionType);
}
