package swdchatbox.system.payment.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.payment.config.VnpayProperties;
import swdchatbox.system.payment.dto.response.IpnResponse;
import swdchatbox.system.payment.dto.response.IpnResponse.IpnResponseCode;
import swdchatbox.system.payment.dto.response.PaymentInitResponse;
import swdchatbox.system.payment.dto.response.PaymentResponse;
import swdchatbox.system.payment.dto.response.PaymentReturnResponse;
import swdchatbox.system.payment.entity.PaymentTransaction;
import swdchatbox.system.payment.entity.PaymentTransaction.PaymentChannel;
import swdchatbox.system.payment.entity.PaymentTransaction.PaymentMethod;
import swdchatbox.system.payment.entity.PaymentTransaction.TransactionType;
import swdchatbox.system.payment.enums.PaymentStatus;
import swdchatbox.system.payment.repository.PaymentTransactionRepository;
import swdchatbox.system.payment.util.VnpayUtil;
import swdchatbox.system.wallet.service.WalletService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VnpayPaymentService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final BigDecimal AMOUNT_MULTIPLIER = BigDecimal.valueOf(100);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WalletService walletService;
    private final VnpayProperties vnpay;

    /* ===================== Tạo giao dịch nạp ví ===================== */

    @Transactional
    public PaymentInitResponse createTopUpPayment(String email,
                                                  BigDecimal amount,
                                                  String bankCode,
                                                  HttpServletRequest request) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than 0");
        }

        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        String createDate = now.format(VNP_DATE_FORMAT);
        String expireDate = now.plusMinutes(vnpay.getExpireMinutes()).format(VNP_DATE_FORMAT);
        String txnRef = createDate + VnpayUtil.randomNumber(6);
        String clientIp = VnpayUtil.resolveIpAddress(request);
        String orderInfo = "Nap vi SWDChatBox - " + email;

        PaymentTransaction tx = paymentTransactionRepository.save(PaymentTransaction.builder()
                .vnpTxnRef(txnRef)
                .userEmail(email)
                .amount(amount)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.WALLET_TOPUP)
                .paymentChannel(PaymentChannel.VNPAY)
                .transactionType(TransactionType.PAYMENT)
                .gateway("VNPAY")
                .orderInfo(orderInfo)
                .clientIp(clientIp)
                .description("Student wallet top up")
                .referenceType("WALLET")
                .build());

        String paymentUrl = buildPaymentUrl(tx, bankCode, createDate, expireDate);

        log.info("Created VNPAY top-up payment txnRef={} amount={} for {}", txnRef, amount, email);

        return PaymentInitResponse.builder()
                .txnRef(txnRef)
                .amount(amount)
                .paymentUrl(paymentUrl)
                .build();
    }

    private String buildPaymentUrl(PaymentTransaction tx, String bankCode, String createDate, String expireDate) {
        long vnpAmount = tx.getAmount().multiply(AMOUNT_MULTIPLIER).longValueExact();

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", vnpay.getVersion());
        params.put("vnp_Command", vnpay.getCommand());
        params.put("vnp_TmnCode", vnpay.getTmnCode());
        params.put("vnp_Amount", String.valueOf(vnpAmount));
        params.put("vnp_CurrCode", vnpay.getCurrencyCode());
        params.put("vnp_TxnRef", tx.getVnpTxnRef());
        params.put("vnp_OrderInfo", tx.getOrderInfo());
        params.put("vnp_OrderType", vnpay.getOrderType());
        params.put("vnp_Locale", vnpay.getLocale());
        params.put("vnp_ReturnUrl", vnpay.getReturnUrl());
        params.put("vnp_IpAddr", tx.getClientIp());
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", expireDate);
        if (bankCode != null && !bankCode.isBlank()) {
            params.put("vnp_BankCode", bankCode.trim());
        }

        String hashData = VnpayUtil.buildHashData(params);
        String query = VnpayUtil.buildQuery(params);
        String secureHash = VnpayUtil.hmacSHA512(vnpay.getHashSecret(), hashData);

        return vnpay.getPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    /* ===================== IPN (server-to-server) ===================== */

    @Transactional
    public IpnResponse handleIpn(Map<String, String> params) {
        if (!verifySignature(params)) {
            log.warn(
                    "VNPAY IPN invalid signature for txnRef={} receivedTmnCode={} configuredTmnCode={} "
                            + "hashSecretConfigured={} paramKeys={}",
                    params.get("vnp_TxnRef"),
                    params.get("vnp_TmnCode"),
                    vnpay.getTmnCode(),
                    vnpay.getHashSecret() != null && !vnpay.getHashSecret().isBlank(),
                    params.keySet());
            return IpnResponse.of(IpnResponseCode.INVALID_SIGNATURE);
        }

        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        String amountRaw = params.get("vnp_Amount");

        PaymentTransaction tx = paymentTransactionRepository.findByVnpTxnRef(txnRef).orElse(null);
        if (tx == null) {
            return IpnResponse.of(IpnResponseCode.ORDER_NOT_FOUND);
        }

        long expectedAmount = tx.getAmount().multiply(AMOUNT_MULTIPLIER).longValueExact();
        if (amountRaw == null || !amountRaw.equals(String.valueOf(expectedAmount))) {
            return IpnResponse.of(IpnResponseCode.INVALID_AMOUNT);
        }

        if (tx.getPaymentStatus() != PaymentStatus.PENDING) {
            return IpnResponse.of(IpnResponseCode.ORDER_ALREADY_CONFIRMED);
        }

        tx.setChecksumOk(true);
        tx.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        tx.setVnpResponseCode(responseCode);
        tx.setVnpTransactionStatus(transactionStatus);
        tx.setVnpBankCode(params.get("vnp_BankCode"));
        tx.setVnpPayDate(params.get("vnp_PayDate"));

        boolean paid = "00".equals(responseCode) && "00".equals(transactionStatus);
        if (paid) {
            tx.setPaymentStatus(PaymentStatus.SUCCESS);
            paymentTransactionRepository.save(tx);
            walletService.creditWallet(tx.getUserEmail(), tx.getAmount(), tx.getVnpTxnRef(), tx.getDescription());
            log.info("VNPAY IPN success: credited wallet {} amount={} txnRef={}",
                    tx.getUserEmail(), tx.getAmount(), txnRef);
        } else {
            tx.setPaymentStatus(PaymentStatus.FAILED);
            paymentTransactionRepository.save(tx);
            log.info("VNPAY IPN failed txnRef={} responseCode={} transactionStatus={}",
                    txnRef, responseCode, transactionStatus);
        }

        return IpnResponse.of(IpnResponseCode.SUCCESS);
    }

    /* ===================== Return URL (redirect người dùng) ===================== */

    @Transactional(readOnly = true)
    public PaymentReturnResponse handleReturn(Map<String, String> params) {
        boolean validSignature = verifySignature(params);
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");

        PaymentTransaction tx = txnRef == null ? null
                : paymentTransactionRepository.findByVnpTxnRef(txnRef).orElse(null);

        boolean success = validSignature && "00".equals(responseCode)
                && "00".equals(params.get("vnp_TransactionStatus"));

        return PaymentReturnResponse.builder()
                .success(success)
                .validSignature(validSignature)
                .txnRef(txnRef)
                .amount(tx != null ? tx.getAmount() : null)
                .status(tx != null ? tx.getPaymentStatus() : null)
                .responseCode(responseCode)
                .message(success ? "Giao dịch thành công"
                        : !validSignature ? "Sai chữ ký (checksum)"
                        : "Giao dịch không thành công")
                .build();
    }

    /* ===================== Truy vấn lịch sử ===================== */

    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyPayments(String email) {
        return paymentTransactionRepository.findAllByUserEmailOrderByCreatedAtDesc(email)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByTxnRef(String txnRef, String email) {
        PaymentTransaction tx = paymentTransactionRepository.findByVnpTxnRef(txnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found"));
        if (!tx.getUserEmail().equals(email)) {
            throw new BadRequestException("You can only view your own payments");
        }
        return toResponse(tx);
    }

    /* ===================== Helpers ===================== */

    private boolean verifySignature(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        Map<String, String> signed = new TreeMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)
                    && value != null
                    && !value.isEmpty()) {
                signed.put(key, value);
            }
        });
        String hashData = VnpayUtil.buildHashData(signed);
        String expected = VnpayUtil.hmacSHA512(vnpay.getHashSecret(), hashData);
        return expected.equalsIgnoreCase(receivedHash);
    }

    private PaymentResponse toResponse(PaymentTransaction tx) {
        return PaymentResponse.builder()
                .id(tx.getId())
                .txnRef(tx.getVnpTxnRef())
                .userEmail(tx.getUserEmail())
                .amount(tx.getAmount())
                .status(tx.getPaymentStatus())
                .method(tx.getPaymentMethod() != null ? tx.getPaymentMethod().name() : null)
                .channel(tx.getPaymentChannel() != null ? tx.getPaymentChannel().name() : null)
                .transactionType(tx.getTransactionType() != null ? tx.getTransactionType().name() : null)
                .gateway(tx.getGateway())
                .orderInfo(tx.getOrderInfo())
                .description(tx.getDescription())
                .referenceType(tx.getReferenceType())
                .referenceId(tx.getReferenceId())
                .vnpTransactionNo(tx.getVnpTransactionNo())
                .vnpResponseCode(tx.getVnpResponseCode())
                .vnpBankCode(tx.getVnpBankCode())
                .vnpPayDate(tx.getVnpPayDate())
                .checksumOk(tx.getChecksumOk())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }
}
