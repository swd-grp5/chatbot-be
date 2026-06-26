package swdchatbox.modules.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import swdchatbox.modules.invoice.entity.Invoice;
import swdchatbox.modules.payment.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_ref", columnList = "vnp_txn_ref"),
        @Index(name = "idx_payment_status", columnList = "payment_status"),
        @Index(name = "idx_payment_invoice_id", columnList = "invoice_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vnp_txn_ref", nullable = false, unique = true, length = 100)
    private String vnpTxnRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_channel", nullable = false, length = 50)
    private PaymentChannel paymentChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "gateway", length = 50)
    private String gateway;

    @Column(name = "vnp_transaction_no", length = 100)
    private String vnpTransactionNo;

    @Column(name = "vnp_response_code", length = 10)
    private String vnpResponseCode;

    @Column(name = "vnp_transaction_status", length = 10)
    private String vnpTransactionStatus;

    @Column(name = "vnp_bank_code", length = 20)
    private String vnpBankCode;

    @Column(name = "vnp_pay_date", length = 20)
    private String vnpPayDate;

    @Column(name = "checksum_ok")
    private Boolean checksumOk;

    @Column(name = "order_info", length = 255)
    private String orderInfo;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "description", length = 255)
    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
        if (paymentStatus == null) paymentStatus = PaymentStatus.PENDING;
        if (transactionType == null) transactionType = TransactionType.PAYMENT;
        if (paymentChannel == null) paymentChannel = PaymentChannel.VNPAY;
        if (paymentMethod == null) paymentMethod = PaymentMethod.WALLET_TOPUP;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PaymentMethod { WALLET_TOPUP, SUBSCRIPTION, REFUND, PENALTY }
    public enum PaymentChannel { VNPAY, WALLET, CASH, NONE }
    public enum TransactionType { PAYMENT, REFUND }
}
