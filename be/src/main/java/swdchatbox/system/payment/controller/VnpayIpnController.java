package swdchatbox.system.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swdchatbox.system.payment.dto.response.IpnResponse;
import swdchatbox.system.payment.dto.response.IpnResponse.IpnResponseCode;
import swdchatbox.system.payment.service.VnpayPaymentService;

import java.util.Map;
import java.util.TreeMap;

/**
 * Endpoint nhận IPN (Instant Payment Notification) từ VNPAY theo cơ chế server-to-server.
 * Đây là URL merchant phải khai báo với VNPAY. Endpoint này KHÔNG yêu cầu xác thực JWT.
 *
 * Trả về đúng định dạng VNPAY yêu cầu: {"RspCode":"00","Message":"Confirm Success"}.
 */
@Slf4j
@RestController
@RequestMapping("/payments/vnpay")
@RequiredArgsConstructor
public class VnpayIpnController {

    private final VnpayPaymentService vnpayPaymentService;

    @Operation(summary = "VNPAY IPN", description = "VNPAY gọi server-to-server để cập nhật trạng thái thanh toán. Merchant khai báo URL này với VNPAY.")
    @GetMapping("/ipn")
    @PostMapping("/ipn")
    public ResponseEntity<IpnResponse> ipn(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(vnpayPaymentService.handleIpn(extractVnpayParams(request)));
        } catch (Exception ex) {
            log.error("VNPAY IPN processing error", ex);
            return ResponseEntity.ok(IpnResponse.of(IpnResponseCode.UNKNOWN_ERROR));
        }
    }

    private static Map<String, String> extractVnpayParams(HttpServletRequest request) {
        Map<String, String> params = new TreeMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!key.startsWith("vnp_") || values == null || values.length == 0) {
                return;
            }
            String value = values[0];
            if (value != null && !value.isEmpty()) {
                params.put(key, value);
            }
        });
        return params;
    }
}
