package swdchatbox.system.wallet.dto.response;

import lombok.Builder;
import swdchatbox.system.wallet.enums.WalletTransactionStatus;
import swdchatbox.system.wallet.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record WalletTransactionResponse(
        UUID id,
        UUID walletId,
        WalletTransactionType transactionType,
        WalletTransactionStatus status,
        BigDecimal amount,
        String referenceId,
        String description,
        LocalDateTime createdAt
) {}
