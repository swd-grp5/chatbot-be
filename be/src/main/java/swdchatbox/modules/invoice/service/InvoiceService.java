package swdchatbox.modules.invoice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.invoice.dto.response.InvoiceResponse;
import swdchatbox.modules.invoice.entity.Invoice;
import swdchatbox.modules.invoice.enums.InvoiceStatus;
import swdchatbox.modules.invoice.enums.InvoiceType;
import swdchatbox.modules.invoice.repository.InvoiceRepository;
import swdchatbox.modules.payment.entity.PaymentTransaction;
import swdchatbox.modules.payment.repository.PaymentTransactionRepository;
import swdchatbox.modules.subscription.entity.UserSubscription;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.repository.UserRepository;
import swdchatbox.modules.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public Invoice createSubscriptionInvoice(UserSubscription subscription) {
        BigDecimal amount = subscription.getSubscriptionPlan().getPrice();
        return createSubscriptionInvoice(subscription, amount != null ? amount : BigDecimal.ZERO);
    }

    @Transactional
    public Invoice createSubscriptionInvoice(UserSubscription subscription, BigDecimal amount) {
        BigDecimal resolvedAmount = amount != null ? amount : BigDecimal.ZERO;
        boolean paid = resolvedAmount.signum() == 0;
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        String planName = subscription.getSubscriptionPlan().getName();

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber())
                .userSubscription(subscription)
                .type(InvoiceType.SUBSCRIPTION)
                .amount(resolvedAmount)
                .status(paid ? InvoiceStatus.PAID : InvoiceStatus.PENDING)
                .planName(planName)
                .description("Subscription invoice for plan " + planName)
                .issuedAt(now)
                .paidAt(paid ? now : null)
                .build();

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice createWalletTopUpInvoice(Wallet wallet, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber())
                .wallet(wallet)
                .type(InvoiceType.WALLET_TOPUP)
                .amount(amount)
                .status(InvoiceStatus.PENDING)
                .planName("Wallet Top-Up")
                .description("Wallet top-up")
                .issuedAt(now)
                .build();

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice markPaid(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now(VN_ZONE));
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> findMyInvoices(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return invoiceRepository.findAllByOwnerUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findById(UUID id, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Invoice invoice = invoiceRepository.findByIdAndOwnerUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        return toResponse(invoice);
    }

    private String generateInvoiceNumber() {
        String datePart = LocalDateTime.now(VN_ZONE).format(NUMBER_DATE);
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "INV-" + datePart + "-" + random;
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        PaymentTransaction payment = paymentTransactionRepository.findByInvoice_Id(invoice.getId()).orElse(null);
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .subscriptionPlanId(invoice.getUserSubscription() != null
                        ? invoice.getUserSubscription().getSubscriptionPlan().getId()
                        : null)
                .planName(invoice.getPlanName())
                .amount(invoice.getAmount())
                .status(invoice.getStatus())
                .description(invoice.getDescription())
                .issuedAt(invoice.getIssuedAt())
                .paidAt(invoice.getPaidAt())
                .paymentId(payment != null ? payment.getId() : null)
                .paymentTxnRef(payment != null ? payment.getVnpTxnRef() : null)
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .build();
    }
}
