package swdchatbox.system.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WalletTopUpRequest(
        @Schema(description = "Số tiền cần nạp vào ví (VND)", example = "100000")
        @NotNull
        @DecimalMin(value = "1000", message = "Số tiền nạp tối thiểu là 1.000 VND")
        BigDecimal amount,

        @Schema(hidden = true)
        String bankCode
) {
    public WalletTopUpRequest {
        if (bankCode == null || bankCode.isBlank()) {
            bankCode = "NCB";
        }
    }
}
