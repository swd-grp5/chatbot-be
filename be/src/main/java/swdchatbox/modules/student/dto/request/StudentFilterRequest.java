package swdchatbox.modules.student.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class StudentFilterRequest {
    private Boolean active;
    private String keyword;
    private UUID subjectId;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
}
