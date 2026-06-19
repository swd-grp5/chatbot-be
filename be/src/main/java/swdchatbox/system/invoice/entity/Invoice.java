package swdchatbox.system.invoice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import swdchatbox.system.invoice.enums.InvoiceStatus;
import swdchatbox.system.invoice.enums.InvoiceType;
import swdchatbox.system.subscription.entity.UserSubscription;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoice_user_subscription", columnList = "user_subscription_id"),
        @Index(name = "idx_invoice_wallet", columnList = "wallet_id"),
        @Index(name = "idx_invoice_number", columnList = "invoice_number", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_subscription_id")
    private UserSubscription userSubscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(length = 255)
    private String description;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public User resolveOwner() {
        if (userSubscription != null) {
            return userSubscription.getUser();
        }
        if (wallet != null) {
            return wallet.getUser();
        }
        throw new IllegalStateException("Invoice has no owner reference");
    }
}
