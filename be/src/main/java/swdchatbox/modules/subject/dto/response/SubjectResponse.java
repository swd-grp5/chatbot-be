package swdchatbox.modules.subject.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonPropertyOrder({"id", "code", "name", "description", "userId", "userName", "active", "createdAt", "updatedAt"})
public class SubjectResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private UUID userId;
    private String userName;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
