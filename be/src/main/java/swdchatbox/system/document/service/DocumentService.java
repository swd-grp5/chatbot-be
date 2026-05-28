package swdchatbox.system.document.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.dto.request.DocumentFilterRequest;
import swdchatbox.system.document.dto.request.DocumentUpdateRequest;
import swdchatbox.system.document.dto.request.DocumentUploadRequest;
import swdchatbox.system.document.dto.response.DocumentFileResponse;
import swdchatbox.system.document.dto.response.DocumentResponse;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.entity.DocumentFile;
import swdchatbox.system.document.entity.Subject;
import swdchatbox.system.document.enums.DocumentStatus;
import swdchatbox.system.document.repository.DocumentFileRepository;
import swdchatbox.system.document.repository.DocumentRepository;
import swdchatbox.system.document.repository.DocumentSpecifications;
import swdchatbox.system.document.repository.SubjectRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final SubjectRepository subjectRepository;
    private final DocumentStorageService documentStorageService;

    @Transactional
    public DocumentResponse upload(DocumentUploadRequest request, List<MultipartFile> files) {
        Subject subject = findSubject(request.getSubjectId());

        Document document = Document.builder()
                .subject(subject)
                .title(request.getTitle())
                .description(request.getDescription())
                .documentType(request.getDocumentType())
                .status(DocumentStatus.UPLOADED)
                .totalPages(0)
                .totalChunks(0)
                .extractedText("")
                .active(true)
                .build();

        document = documentRepository.save(document);
        saveFiles(document, files);
        return toResponse(document);
    }

    public Page<DocumentResponse> findAll(DocumentFilterRequest filter, Pageable pageable) {
        Specification<Document> spec = Specification
                .where(DocumentSpecifications.hasSubjectId(filter != null ? filter.getSubjectId() : null))
                .and(DocumentSpecifications.hasDocumentType(filter != null ? filter.getDocumentType() : null))
                .and(DocumentSpecifications.hasActive(filter != null ? filter.getActive() : null));

        return documentRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public DocumentResponse findById(UUID id) {
        return toResponse(findDocument(id));
    }

    @Transactional
    public DocumentResponse update(UUID id, DocumentUpdateRequest request, List<MultipartFile> files) {
        Document document = findDocument(id);
        Subject subject = findSubject(request.getSubjectId());

        document.setSubject(subject);
        document.setTitle(request.getTitle());
        document.setDescription(request.getDescription());
        document.setDocumentType(request.getDocumentType());
        if (request.getActive() != null) {
            document.setActive(request.getActive());
        }

        documentRepository.save(document);
        if (files != null && !files.isEmpty()) {
            saveFiles(document, files);
        }
        return toResponse(document);
    }

    @Transactional
    public DocumentResponse addFiles(UUID id, List<MultipartFile> files) {
        Document document = findDocument(id);
        saveFiles(document, files);
        return toResponse(document);
    }

    @Transactional
    public void delete(UUID id) {
        Document document = findDocument(id);
        List<DocumentFile> documentFiles = documentFileRepository.findAllByDocument_Id(id);
        for (DocumentFile file : documentFiles) {
            documentStorageService.deleteFile(file.getFilePath());
        }
        documentStorageService.deleteDocumentFolder(id);
        documentFileRepository.deleteAllByDocument_Id(id);
        documentRepository.delete(document);
    }

    private void saveFiles(Document document, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            var stored = documentStorageService.store(document.getId(), file);
            DocumentFile documentFile = DocumentFile.builder()
                    .document(document)
                    .originalFileName(stored.originalFileName())
                    .storedFileName(stored.storedFileName())
                    .filePath(stored.filePath())
                    .mimeType(file.getContentType())
                    .fileSize(file.getSize())
                    .checksum(stored.checksum())
                    .build();
            documentFileRepository.save(documentFile);
        }
    }

    private Subject findSubject(String subjectId) {
        try {
            return subjectRepository.findById(UUID.fromString(subjectId))
                    .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid subjectId");
        }
    }

    private Document findDocument(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private DocumentResponse toResponse(Document document) {
        List<DocumentFileResponse> files = documentFileRepository.findAllByDocument_Id(document.getId())
                .stream()
                .map(DocumentFileResponse::from)
                .toList();

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
                .extractedText(document.getExtractedText())
                .active(document.getActive())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .files(files)
                .build();
    }
}
