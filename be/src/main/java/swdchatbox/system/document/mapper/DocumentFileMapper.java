package swdchatbox.system.document.mapper;

import swdchatbox.system.document.dto.response.DocumentFileResponse;
import swdchatbox.system.document.entity.DocumentFile;

public final class DocumentFileMapper {

    private DocumentFileMapper() {
    }

    public static DocumentFileResponse toResponse(DocumentFile file) {
        return DocumentFileResponse.builder()
                .id(file.getId())
                .originalFileName(file.getOriginalFileName())
                .storedFileName(file.getStoredFileName())
                .filePath(file.getFilePath())
                .mimeType(file.getMimeType())
                .fileSize(file.getFileSize())
                .checksum(file.getChecksum())
                .createdAt(file.getCreatedAt())
                .build();
    }
}
