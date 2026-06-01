package swdchatbox.system.document.mapper;

import swdchatbox.system.document.dto.response.DocumentDashboardStatsResponse;
import swdchatbox.system.document.dto.response.DocumentFileResponse;
import swdchatbox.system.document.dto.response.DocumentResponse;
import swdchatbox.system.document.entity.Document;

public final class DocumentMapper {

    private DocumentMapper() {
    }

    public static DocumentResponse toResponse(Document document, java.util.List<DocumentFileResponse> files) {
        return DocumentResponse.builder()
                .id(document.getId())
                .subjectId(document.getSubject() != null ? document.getSubject().getId() : null)
                .subjectCode(document.getSubject() != null ? document.getSubject().getCode() : null)
                .subjectName(document.getSubject() != null ? document.getSubject().getName() : null)
                .title(document.getTitle())
                .description(document.getDescription())
                .documentType(document.getDocumentType())
                .status(document.getStatus())
                .totalPages(document.getTotalPages())
                .totalChunks(document.getTotalChunks())
                .active(document.getActive())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .files(files)
                .build();
    }

    public static DocumentDashboardStatsResponse toStatsResponse(long totalDocuments, long readyDocuments, long processingDocuments, long failedDocuments, long uploadedDocuments) {
        return DocumentDashboardStatsResponse.builder()
                .totalDocuments(totalDocuments)
                .readyDocuments(readyDocuments)
                .processingDocuments(processingDocuments)
                .failedDocuments(failedDocuments)
                .uploadedDocuments(uploadedDocuments)
                .build();
    }
}
