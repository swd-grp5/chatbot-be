package swdchatbox.modules.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body trả về cho VNPAY khi nhận IPN (server-to-server).
 * VNPAY yêu cầu đúng định dạng: {"RspCode":"00","Message":"Confirm Success"}.
 */
public record IpnResponse(
        @JsonProperty("RspCode") String rspCode,
        @JsonProperty("Message") String message
) {
    public static IpnResponse of(IpnResponseCode code) {
        return new IpnResponse(code.getCode(), code.getMessage());
    }

    public enum IpnResponseCode {
        SUCCESS("00", "Confirm Success"),
        ORDER_NOT_FOUND("01", "Order not found"),
        ORDER_ALREADY_CONFIRMED("02", "Order already confirmed"),
        INVALID_AMOUNT("04", "Invalid amount"),
        INVALID_SIGNATURE("97", "Invalid Checksum"),
        UNKNOWN_ERROR("99", "Unknown error");

        private final String code;
        private final String message;

        IpnResponseCode(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
