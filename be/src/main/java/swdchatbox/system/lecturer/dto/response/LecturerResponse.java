package swdchatbox.system.lecturer.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import swdchatbox.system.subject.dto.response.SubjectSummaryResponse;
import swdchatbox.system.user.enums.AuthProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@JsonPropertyOrder({"id", "fullName", "email", "subjects", "active", "emailVerified", "provider", "createdAt", "updatedAt"})
public record LecturerResponse(
        UUID id,
        String fullName,
        String email,
        List<SubjectSummaryResponse> subjects,
        Boolean active,
        Boolean emailVerified,
        AuthProvider provider,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
