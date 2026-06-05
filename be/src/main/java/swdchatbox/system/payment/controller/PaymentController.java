package swdchatbox.system.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.payment.dto.response.PaymentResponse;
import swdchatbox.system.payment.dto.response.PaymentReturnResponse;
import swdchatbox.system.payment.service.VnpayPaymentService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final VnpayPaymentService vnpayPaymentService;

    @Operation(summary = "Lịch sử thanh toán của tôi", description = "FE dùng để hiển thị lịch sử giao dịch thanh toán (nạp ví, ...) của user đang đăng nhập.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<List<PaymentResponse>> myPayments(Authentication authentication) {
        return ResponseEntity.ok(vnpayPaymentService.getMyPayments(authentication.getName()));
    }

    @Operation(summary = "Chi tiết một giao dịch thanh toán", description = "Tra cứu chi tiết giao dịch theo mã giao dịch (vnp_TxnRef) của chính user.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{txnRef}")
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "Mã giao dịch vnp_TxnRef") @PathVariable String txnRef,
            Authentication authentication
    ) {
        return ResponseEntity.ok(vnpayPaymentService.getByTxnRef(txnRef, authentication.getName()));
    }

    @Operation(summary = "VNPAY Return URL", description = "VNPAY redirect người dùng về đây sau khi thanh toán. Endpoint public, chỉ dùng để FE hiển thị kết quả; trạng thái ví được cập nhật qua IPN.")
    @GetMapping("/vnpay/return")
    public ResponseEntity<PaymentReturnResponse> vnpayReturn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(vnpayPaymentService.handleReturn(params));
    }
}
