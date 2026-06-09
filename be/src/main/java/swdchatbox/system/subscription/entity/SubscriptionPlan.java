package swdchatbox.system.subscription.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)") // Khớp với binary(16) trong SQL của bạn
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, precision = 38, scale = 2) // Khớp với decimal(38, 2)
    private BigDecimal price;

    @Column(name = "daily_question_limit", nullable = false)
    private Integer dailyQuestionLimit;

    @Column(name = "duration_in_months", nullable = false)
    private Integer durationInMonths;

    @Column(columnDefinition = "TEXT") // Khớp với text null trong SQL của bạn
    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}