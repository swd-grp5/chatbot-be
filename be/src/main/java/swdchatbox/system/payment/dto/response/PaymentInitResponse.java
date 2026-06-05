package swdchatbox.system.payment.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Trả về cho FE sau khi tạo giao dịch: FE redirect user sang {@code paymentUrl}.
 */
@Builder
public record PaymentInitResponse(
        String txnRef,
        BigDecimal amount,
        String paymentUrl
) {}
