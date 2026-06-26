package swdchatbox.modules.wallet.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record WalletResponse(
        UUID id,
        BigDecimal balance,
        BigDecimal reservedBalance,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
