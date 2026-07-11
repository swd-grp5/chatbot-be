package swdchatbox.system.menu.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_group_id", nullable = false)
    private MenuGroup menuGroup;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String url;

    @Column(length = 50)
    private String icon;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Role code required to see this item (e.g. ADMIN). Null = visible to all authenticated users. */
    @Column(length = 50)
    private String requiredRole;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
