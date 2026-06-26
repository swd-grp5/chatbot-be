package swdchatbox.modules.payment.dto.response;

import lombok.Builder;
import swdchatbox.modules.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PaymentResponse(
        UUID id,
        String txnRef,
        String userEmail,
        UUID invoiceId,
        BigDecimal amount,
        PaymentStatus status,
        String method,
        String channel,
        String transactionType,
        String gateway,
        String orderInfo,
        String description,
        String vnpTransactionNo,
        String vnpResponseCode,
        String vnpBankCode,
        String vnpPayDate,
        Boolean checksumOk,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
