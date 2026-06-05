package swdchatbox.system.payment.dto.response;

import lombok.Builder;
import swdchatbox.system.payment.enums.PaymentStatus;

import java.math.BigDecimal;

/**
 * Kết quả hiển thị cho FE khi VNPAY redirect người dùng về ReturnUrl.
 * Đây chỉ để hiển thị UI, trạng thái thật của ví được cập nhật qua IPN.
 */
@Builder
public record PaymentReturnResponse(
        boolean success,
        boolean validSignature,
        String txnRef,
        BigDecimal amount,
        PaymentStatus status,
        String responseCode,
        String message
) {}
