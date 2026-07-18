package swdchatbox.modules.credit.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditResponse {
    private Integer remainingCredits;
    private LocalDateTime periodStartedAt;
    private LocalDateTime nextResetAt;
}
