package swdchatbox.system.wallet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swdchatbox.system.wallet.entity.Wallet;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByUser_Id(UUID userId);
}
