package swdchatbox.modules.document.mapper;

import swdchatbox.modules.document.dto.response.DocumentFileResponse;
import swdchatbox.modules.document.entity.DocumentFile;

public final class DocumentFileMapper {

    private DocumentFileMapper() {
    }

    public static DocumentFileResponse toResponse(DocumentFile file) {
        return DocumentFileResponse.builder()
                .id(file.getId())
                .originalFileName(file.getOriginalFileName())
                .mimeType(file.getMimeType())
                .fileSize(file.getFileSize())
                .createdAt(file.getCreatedAt())
                .build();
    }
}
