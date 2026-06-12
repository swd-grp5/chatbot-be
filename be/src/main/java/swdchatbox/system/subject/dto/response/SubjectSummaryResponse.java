package swdchatbox.system.subject.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;

import java.util.UUID;

@Builder
@JsonPropertyOrder({"id", "code", "name", "totalDocuments"})
public record SubjectSummaryResponse(
        UUID id,
        String code,
        String name,
        long totalDocuments
) {
}
