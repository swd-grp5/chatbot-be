package swdchatbox.system.payment.dto.response;

import lombok.Builder;
import swdchatbox.system.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PaymentResponse(
        UUID id,
        String txnRef,
        String userEmail,
        BigDecimal amount,
        PaymentStatus status,
        String method,
        String channel,
        String transactionType,
        String gateway,
        String orderInfo,
        String description,
        String referenceType,
        String referenceId,
        String vnpTransactionNo,
        String vnpResponseCode,
        String vnpBankCode,
        String vnpPayDate,
        Boolean checksumOk,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
