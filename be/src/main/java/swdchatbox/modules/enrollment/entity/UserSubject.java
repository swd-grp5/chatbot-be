package swdchatbox.modules.enrollment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import swdchatbox.modules.subject.entity.Subject;
import swdchatbox.modules.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "user_subjects",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_subject",
                columnNames = {"user_id", "subject_id"}
        ),
        indexes = {
                @Index(name = "idx_user_subject_user", columnList = "user_id"),
                @Index(name = "idx_user_subject_subject", columnList = "subject_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
