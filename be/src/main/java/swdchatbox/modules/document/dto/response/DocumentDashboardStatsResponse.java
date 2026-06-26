package swdchatbox.modules.document.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonPropertyOrder({"totalDocuments", "readyDocuments", "processingDocuments", "failedDocuments", "uploadedDocuments"})
public class DocumentDashboardStatsResponse {
    private long totalDocuments;
    private long readyDocuments;
    private long processingDocuments;
    private long failedDocuments;
    private long uploadedDocuments;
}
