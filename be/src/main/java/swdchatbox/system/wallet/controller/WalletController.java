package swdchatbox.system.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.payment.dto.request.WalletTopUpRequest;
import swdchatbox.system.payment.dto.response.PaymentInitResponse;
import swdchatbox.system.payment.service.VnpayPaymentService;
import swdchatbox.system.wallet.dto.response.WalletResponse;
import swdchatbox.system.wallet.dto.response.WalletTransactionResponse;
import swdchatbox.system.wallet.service.WalletService;

import java.util.List;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;
    private final VnpayPaymentService vnpayPaymentService;

    @Operation(summary = "Lấy thông tin ví của tôi", description = "FE dùng để hiển thị số dư ví. Ví sẽ được tạo tự động nếu user chưa có.")
    @GetMapping("/me")
    public ResponseEntity<WalletResponse> myWallet(Authentication authentication) {
        return ResponseEntity.ok(walletService.getMyWallet(authentication.getName()));
    }

    @Operation(summary = "Lịch sử giao dịch ví", description = "FE dùng để hiển thị lịch sử nạp/trừ tiền trong ví của user đang đăng nhập.")
    @GetMapping("/me/transactions")
    public ResponseEntity<List<WalletTransactionResponse>> myTransactions(Authentication authentication) {
        return ResponseEntity.ok(walletService.getMyTransactionHistory(authentication.getName()));
    }

    @Operation(summary = "Nạp tiền vào ví qua VNPAY", description = "Tạo giao dịch nạp ví và trả về paymentUrl. FE redirect user sang paymentUrl để thanh toán; ví sẽ được cộng tiền khi VNPAY gọi IPN thành công.")
    @PostMapping("/top-up")
    public ResponseEntity<PaymentInitResponse> topUp(
            @Valid @RequestBody WalletTopUpRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        PaymentInitResponse response = vnpayPaymentService.createTopUpPayment(
                authentication.getName(),
                request.amount(),
                request.bankCode(),
                httpRequest
        );
        return ResponseEntity.ok(response);
    }
}
