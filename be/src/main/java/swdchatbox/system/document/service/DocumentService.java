package swdchatbox.system.document.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import swdchatbox.system.common.dto.PageResponse;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.dto.request.DocumentFilterRequest;
import swdchatbox.system.document.dto.request.DocumentUpdateRequest;
import swdchatbox.system.document.dto.request.DocumentUploadRequest;
import swdchatbox.system.document.dto.response.DocumentDashboardStatsResponse;
import swdchatbox.system.document.dto.response.DocumentIndexStatusResponse;
import swdchatbox.system.document.dto.response.DocumentResponse;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.entity.DocumentFile;
import swdchatbox.system.document.enums.DocumentStatus;
import swdchatbox.system.document.enums.DocumentType;
import swdchatbox.system.document.mapper.DocumentFileMapper;
import swdchatbox.system.document.mapper.DocumentMapper;
import swdchatbox.system.document.repository.DocumentFileRepository;
import swdchatbox.system.document.repository.DocumentRepository;
import swdchatbox.system.document.repository.DocumentSpecifications;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.subject.repository.SubjectRepository;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("\\.[^.\\s]+$");

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final SubjectRepository subjectRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentIndexJobService documentIndexJobService;

    @Transactional
    public DocumentResponse upload(DocumentUploadRequest request, List<MultipartFile> files) {
        Subject subject = findDefaultSubject();

        String title = resolveTitle(request.getTitle(), files);

        Document document = Document.builder()
                .subject(subject)
                .title(title)
                .description(request.getDescription())
                .documentType(resolveDocumentType(files))
                .status(DocumentStatus.UPLOADED)
                .totalPages(0)
                .totalChunks(0)
                .extractedText("")
                .active(true)
                .build();

        document = documentRepository.save(document);
        saveFiles(document, files);
        documentIndexJobService.enqueue(document.getId());
        return toResponse(document);
    }

    private String resolveTitle(String requestedTitle, List<MultipartFile> files) {
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            return requestedTitle.trim();
        }
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String originalName = file.getOriginalFilename();
                if (originalName != null && !originalName.isBlank()) {
                    return FILE_EXTENSION_PATTERN.matcher(originalName.trim()).replaceFirst("");
                }
            }
        }
        throw new BadRequestException("Document file name is required when title is empty");
    }

    private DocumentType resolveDocumentType(List<MultipartFile> files) {
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String contentType = file.getContentType();
                if (contentType == null) {
                    break;
                }
                if (contentType.contains("pdf")) {
                    return DocumentType.PDF;
                }
                if (contentType.contains("word") || contentType.contains("officedocument")) {
                    return DocumentType.DOCX;
                }
                if (contentType.contains("presentation")) {
                    return DocumentType.PPTX;
                }
                if (contentType.startsWith("text/")) {
                    return DocumentType.TXT;
                }
                return DocumentType.OTHER;
            }
        }
        return DocumentType.OTHER;
    }

    public DocumentDashboardStatsResponse getStats() {
        return DocumentMapper.toStatsResponse(
                documentRepository.countAllDocuments(),
                documentRepository.countByStatus(DocumentStatus.INDEXED),
                documentRepository.countByStatus(DocumentStatus.PROCESSING),
                documentRepository.countByStatus(DocumentStatus.FAILED),
                documentRepository.countByStatus(DocumentStatus.UPLOADED)
        );
    }

    public PageResponse<DocumentResponse> findAll(DocumentFilterRequest filter, Pageable pageable) {
        Specification<Document> spec = Specification
                .where(DocumentSpecifications.hasSubjectId(filter != null ? filter.getSubjectId() : null))
                .and(DocumentSpecifications.hasDocumentType(filter != null ? filter.getDocumentType() : null))
                .and(DocumentSpecifications.hasStatus(filter != null ? filter.getStatus() : null))
                .and(DocumentSpecifications.hasActive(filter != null ? filter.getActive() : null))
                .and(DocumentSpecifications.keywordLike(filter != null ? filter.getKeyword() : null))
                .and(DocumentSpecifications.createdAfter(filter != null ? filter.getCreatedFrom() : null))
                .and(DocumentSpecifications.createdBefore(filter != null ? filter.getCreatedTo() : null));

        Page<DocumentResponse> page = documentRepository.findAll(spec, pageable).map(this::toResponse);
        return PageResponse.<DocumentResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    public DocumentResponse findById(UUID id) {
        return toResponse(findDocument(id));
    }

    public DocumentIndexStatusResponse getIndexStatus(UUID id) {
        return documentIndexJobService.getStatus(id);
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
            documentIndexJobService.enqueue(document.getId());
        }
        return toResponse(document);
    }

    private Subject findSubject(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return findDefaultSubject();
        }

        try {
            UUID id = UUID.fromString(subjectId.trim());
            return subjectRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid subject id");
        }
    }


    @Transactional
    public DocumentResponse addFiles(UUID id, List<MultipartFile> files) {
        Document document = findDocument(id);
        saveFiles(document, files);
        documentIndexJobService.enqueue(document.getId());
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

    @Transactional
    public DocumentResponse toggleActive(UUID id) {
        Document document = findDocument(id);
        document.setActive(document.getActive() == null || !document.getActive());
        documentRepository.save(document);
        return toResponse(document);
    }

    public DocumentResponse reindex(UUID id) {
        documentIndexJobService.enqueue(id);
        return toResponse(findDocument(id));
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

    private Subject findDefaultSubject() {
        return subjectRepository.findByCode("SWD")
                .orElseThrow(() -> new ResourceNotFoundException("Default subject not found"));
    }

    private Document findDocument(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private DocumentResponse toResponse(Document document) {
        return DocumentMapper.toResponse(
                document,
                documentFileRepository.findAllByDocument_Id(document.getId())
                        .stream()
                        .map(DocumentFileMapper::toResponse)
                        .toList()
        );
    }
}
