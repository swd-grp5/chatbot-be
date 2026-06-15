package swdchatbox.system.wallet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.payment.dto.request.WalletTopUpRequest;
import swdchatbox.system.payment.dto.response.PaymentInitResponse;
import swdchatbox.system.payment.service.VnpayPaymentService;
import swdchatbox.system.wallet.dto.request.WalletTransactionFilterRequest;
import swdchatbox.system.wallet.dto.response.WalletResponse;
import swdchatbox.system.wallet.dto.response.WalletTransactionResponse;
import swdchatbox.system.wallet.enums.WalletTransactionStatus;
import swdchatbox.system.wallet.enums.WalletTransactionType;
import swdchatbox.system.wallet.service.WalletService;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
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

    @Operation(summary = "Lịch sử giao dịch ví", description = """
            FE dùng để hiển thị lịch sử nạp/trừ tiền trong ví của user đang đăng nhập.
            Hỗ trợ filter theo id, walletId, loại giao dịch, trạng thái, referenceId (khớp chính xác),
            keyword (referenceId/description), khoảng số tiền, khoảng thời gian, phân trang và sort.""")
    @GetMapping("/me/transactions")
    public ResponseEntity<PageResponse<WalletTransactionResponse>> myTransactions(
            Authentication authentication,
            @Parameter(description = "Lọc theo id giao dịch")
            @RequestParam(required = false) UUID id,
            @Parameter(description = "Lọc theo walletId")
            @RequestParam(required = false) UUID walletId,
            @Parameter(description = "Lọc theo loại giao dịch: TOP_UP, SUBSCRIPTION_PAYMENT, REFUND")
            @RequestParam(required = false) WalletTransactionType transactionType,
            @Parameter(description = "Lọc theo trạng thái: PENDING, SUCCESS, FAILED")
            @RequestParam(required = false) WalletTransactionStatus status,
            @Parameter(description = "Lọc referenceId khớp chính xác (ví dụ mã vnp_TxnRef)")
            @RequestParam(required = false) String referenceId,
            @Parameter(description = "Từ khóa tìm kiếm mờ theo referenceId hoặc description")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Số tiền tối thiểu")
            @RequestParam(required = false) BigDecimal amountMin,
            @Parameter(description = "Số tiền tối đa")
            @RequestParam(required = false) BigDecimal amountMax,
            @Parameter(description = "Ngày tạo từ (ISO-8601)")
            @RequestParam(required = false) String createdFrom,
            @Parameter(description = "Ngày tạo đến (ISO-8601)")
            @RequestParam(required = false) String createdTo,
            @Parameter(description = "Số trang, bắt đầu từ 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số phần tử trên mỗi trang")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Trường sắp xếp: id, walletId, transactionType, status, amount, referenceId, description, createdAt")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Hướng sắp xếp: asc hoặc desc")
            @RequestParam(required = false) String sortDir
    ) {
        WalletTransactionFilterRequest filter = new WalletTransactionFilterRequest();
        filter.setId(id);
        filter.setWalletId(walletId);
        filter.setTransactionType(transactionType);
        filter.setStatus(status);
        filter.setReferenceId(referenceId);
        filter.setKeyword(keyword);
        filter.setAmountMin(amountMin);
        filter.setAmountMax(amountMax);
        filter.setCreatedFrom(createdFrom != null ? java.time.LocalDateTime.parse(createdFrom) : null);
        filter.setCreatedTo(createdTo != null ? java.time.LocalDateTime.parse(createdTo) : null);

        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy, sortDir));
        return ResponseEntity.ok(walletService.getMyTransactionHistory(authentication.getName(), filter, pageable));
    }

    @Operation(summary = "Nạp tiền vào ví qua VNPAY", description = "Tạo giao dịch nạp ví và trả về paymentUrl. FE redirect user sang paymentUrl để thanh toán; ví sẽ được cộng tiền khi VNPAY gọi IPN thành công.")
    @PostMapping("/top-up")
    public ResponseEntity<PaymentInitResponse> topUp(
            @Valid @RequestBody WalletTopUpRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        log.info("[wallet/top-up] email={} amount={} bankCode={}", authentication.getName(), request.amount(), request.bankCode());
        PaymentInitResponse response = vnpayPaymentService.createTopUpPayment(
                authentication.getName(),
                request.amount(),
                request.bankCode(),
                httpRequest
        );
        log.info("[wallet/top-up] created txnRef={} paymentUrl={}", response.txnRef(), response.paymentUrl());
        return ResponseEntity.ok(response);
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String property = normalizeSortProperty(sortBy);
        String direction = sortDir != null ? sortDir : "desc";
        return "asc".equalsIgnoreCase(direction) ? Sort.by(property).ascending() : Sort.by(property).descending();
    }

    private String normalizeSortProperty(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }
        return switch (sortBy) {
            case "id", "transactionType", "status", "amount", "referenceId", "description", "createdAt" -> sortBy;
            case "walletId" -> "wallet.id";
            default -> "createdAt";
        };
    }
}
