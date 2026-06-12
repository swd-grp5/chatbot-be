package swdchatbox.system.student.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class StudentFilterRequest {
    private Boolean active;
    private String keyword;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
}
