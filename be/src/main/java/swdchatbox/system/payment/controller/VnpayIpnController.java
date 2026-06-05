package swdchatbox.system.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import swdchatbox.system.payment.dto.response.IpnResponse;
import swdchatbox.system.payment.dto.response.IpnResponse.IpnResponseCode;
import swdchatbox.system.payment.service.VnpayPaymentService;

import java.util.Map;

/**
 * Endpoint nhận IPN (Instant Payment Notification) từ VNPAY theo cơ chế server-to-server.
 * Đây là URL merchant phải khai báo với VNPAY. Endpoint này KHÔNG yêu cầu xác thực JWT.
 *
 * Trả về đúng định dạng VNPAY yêu cầu: {"RspCode":"00","Message":"Confirm Success"}.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
public class VnpayIpnController {

    private final VnpayPaymentService vnpayPaymentService;

    @Operation(summary = "VNPAY IPN", description = "VNPAY gọi server-to-server để cập nhật trạng thái thanh toán. Merchant khai báo URL này với VNPAY.")
    @GetMapping("/ipn")
    public ResponseEntity<IpnResponse> ipn(@RequestParam Map<String, String> params) {
        try {
            return ResponseEntity.ok(vnpayPaymentService.handleIpn(params));
        } catch (Exception ex) {
            log.error("VNPAY IPN processing error", ex);
            return ResponseEntity.ok(IpnResponse.of(IpnResponseCode.UNKNOWN_ERROR));
        }
    }
}
