package swdchatbox.modules.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import swdchatbox.modules.wallet.enums.WalletTransactionStatus;
import swdchatbox.modules.wallet.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_wallet_tx_wallet", columnList = "wallet_id"),
        @Index(name = "idx_wallet_tx_reference", columnList = "reference_id"),
        @Index(name = "idx_wallet_tx_type", columnList = "transaction_type"),
        @Index(name = "idx_wallet_tx_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    private WalletTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 100)
    private String referenceId;

    @Column(length = 255)
    private String description;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
