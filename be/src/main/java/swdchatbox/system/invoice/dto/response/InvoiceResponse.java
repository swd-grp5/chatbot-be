package swdchatbox.system.invoice.dto.response;

import lombok.Builder;
import swdchatbox.system.invoice.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        UUID subscriptionPlanId,
        String planName,
        BigDecimal amount,
        InvoiceStatus status,
        String description,
        LocalDateTime issuedAt,
        LocalDateTime paidAt,
        UUID paymentId,
        String paymentTxnRef,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
