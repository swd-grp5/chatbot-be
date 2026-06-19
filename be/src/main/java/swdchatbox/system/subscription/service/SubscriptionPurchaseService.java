package swdchatbox.system.subscription.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.invoice.entity.Invoice;
import swdchatbox.system.invoice.service.InvoiceService;
import swdchatbox.system.payment.entity.PaymentTransaction;
import swdchatbox.system.payment.enums.PaymentStatus;
import swdchatbox.system.payment.repository.PaymentTransactionRepository;
import swdchatbox.system.subscription.entity.SubscriptionPlan;
import swdchatbox.system.subscription.entity.UserSubscription;
import swdchatbox.system.subscription.repository.SubscriptionPlanRepository;
import swdchatbox.system.subscription.repository.UserSubscriptionRepository;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;
import swdchatbox.system.wallet.service.WalletService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SubscriptionPurchaseService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TXN_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final UserSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final InvoiceService invoiceService;

    @Transactional
    public UserSubscription purchase(UUID planId, String email, BigDecimal price) {
        User user = findEligibleUserByEmail(email);

        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        BigDecimal chargeAmount = price != null ? price : plan.getPrice();
        if (chargeAmount == null || chargeAmount.signum() <= 0) {
            throw new BadRequestException("Subscription price must be greater than 0");
        }

        UserSubscription subscription = subscriptionRepository
                .findByUser_IdAndSubscriptionPlan_Id(user.getId(), planId)
                .orElse(
                        UserSubscription.builder()
                                .user(user)
                                .subscriptionPlan(plan)
                                .build()
                );

        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        subscription.setActive(true);
        subscription.setSubscribedAt(now);

        if (plan.getDurationInMonths() != null) {
            subscription.setExpiresAt(now.plusMonths(plan.getDurationInMonths()));
        }

        subscription.setUnsubscribedAt(null);
        UserSubscription saved = subscriptionRepository.save(subscription);

        Invoice invoice = invoiceService.createSubscriptionInvoice(saved, chargeAmount);

        walletService.debitWallet(
                email,
                chargeAmount,
                "SUB-" + planId,
                "Subscription purchase for plan " + plan.getName()
        );

        paymentTransactionRepository.save(PaymentTransaction.builder()
                .vnpTxnRef(generateWalletTxnRef())
                .invoice(invoice)
                .amount(chargeAmount)
                .paymentStatus(PaymentStatus.SUCCESS)
                .paymentMethod(PaymentTransaction.PaymentMethod.SUBSCRIPTION)
                .paymentChannel(PaymentTransaction.PaymentChannel.WALLET)
                .transactionType(PaymentTransaction.TransactionType.PAYMENT)
                .description("Subscription purchase for plan " + plan.getName())
                .build());

        invoiceService.markPaid(invoice);

        return saved;
    }

    private String generateWalletTxnRef() {
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "WLT-SUB-" + LocalDateTime.now(VN_ZONE).format(TXN_DATE) + random;
    }

    private User findEligibleUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!RoleCodes.STUDENT.equals(user.getRole().getCode())) {
            throw new BadRequestException("Only student accounts can buy subscriptions");
        }

        return user;
    }
}
