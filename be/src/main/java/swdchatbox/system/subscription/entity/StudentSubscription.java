package swdchatbox.system.subscription.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import swdchatbox.system.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "student_subscriptions",
        indexes = {
                @Index(name = "idx_student_subscription_student", columnList = "student_id"),
                @Index(name = "idx_student_subscription_active", columnList = "active")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    // 🚀 ĐỔI TỪ SUBJECT SANG SUBSCRIPTION PLAN Ở ĐÂY
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "subscribed_at", nullable = false)
    private LocalDateTime subscribedAt;

    // 🚀 BỔ SUNG NGÀY HẾT HẠN ĐỂ SERVICE GỌI ĐƯỢC HÀM GETTER
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "unsubscribed_at")
    private LocalDateTime unsubscribedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}