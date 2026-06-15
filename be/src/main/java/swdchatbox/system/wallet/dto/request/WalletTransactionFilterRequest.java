package swdchatbox.system.wallet.dto.request;

import lombok.Getter;
import lombok.Setter;
import swdchatbox.system.wallet.enums.WalletTransactionStatus;
import swdchatbox.system.wallet.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class WalletTransactionFilterRequest {
    private UUID userId;
    private UUID id;
    private UUID walletId;
    private WalletTransactionType transactionType;
    private WalletTransactionStatus status;
    private String referenceId;
    private String keyword;
    private BigDecimal amountMin;
    private BigDecimal amountMax;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
}
