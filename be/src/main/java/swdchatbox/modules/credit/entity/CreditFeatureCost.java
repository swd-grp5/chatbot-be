package swdchatbox.modules.credit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_feature_costs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditFeatureCost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "feature_name", nullable = false, unique = true, length = 50)
    private String featureName;

    @Column(name = "credit_cost", nullable = false)
    private Integer creditCost;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
