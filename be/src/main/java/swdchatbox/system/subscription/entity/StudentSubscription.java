package swdchatbox.system.subscription.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "student_subscriptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_subject_subscription", columnNames = {"student_id", "subject_id"})
        },
        indexes = {
                @Index(name = "idx_student_subscription_student", columnList = "student_id"),
                @Index(name = "idx_student_subscription_subject", columnList = "subject_id"),
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private LocalDateTime subscribedAt;

    private LocalDateTime unsubscribedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

