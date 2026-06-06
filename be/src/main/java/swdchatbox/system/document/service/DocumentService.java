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
import swdchatbox.system.document.dto.response.DocumentViewResponse;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.entity.DocumentFile;
import swdchatbox.system.document.enums.DocumentStatus;
import swdchatbox.system.document.enums.DocumentType;
import swdchatbox.system.document.mapper.DocumentFileMapper;
import swdchatbox.system.document.mapper.DocumentMapper;
import swdchatbox.system.document.repository.DocumentChunkRepository;
import swdchatbox.system.document.repository.DocumentFileRepository;
import swdchatbox.system.document.repository.DocumentIndexJobRepository;
import swdchatbox.system.document.repository.DocumentRepository;
import swdchatbox.system.document.repository.DocumentSpecifications;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.subject.repository.SubjectRepository;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("\\.[^.\\s]+$");

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentIndexJobRepository documentIndexJobRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentIndexJobService documentIndexJobService;
    private final DocumentPageCountService documentPageCountService;

    @Transactional
    public List<DocumentResponse> upload(List<DocumentUploadRequest> items, List<MultipartFile> files, String userEmail) {
        List<MultipartFile> validFiles = getValidFiles(files);
        validateUploadFiles(validFiles);

        List<DocumentUploadRequest> metadata = normalizeMetadata(items, validFiles.size());
        Subject subject = findDefaultSubject();
        User uploadedBy = resolveUploadedBy(userEmail);

        validateUploadBatch(subject, validFiles, metadata);

        List<DocumentResponse> responses = new ArrayList<>();
        for (int i = 0; i < validFiles.size(); i++) {
            MultipartFile file = validFiles.get(i);
            DocumentUploadRequest item = metadata.get(i);
            String title = resolveTitle(item.getTitle(), List.of(file));

            Document document = Document.builder()
                    .subject(subject)
                    .uploadedBy(uploadedBy)
                    .title(title)
                    .description(item.getDescription())
                    .documentType(resolveDocumentType(List.of(file)))
                    .status(DocumentStatus.UPLOADED)
                    .totalPages(0)
                    .totalChunks(0)
                    .extractedText("")
                    .active(true)
                    .build();

            document = documentRepository.save(document);
            saveFiles(document, List.of(file));
            refreshTotalPages(document);
            documentIndexJobService.enqueue(document.getId());
            responses.add(toResponse(document));
        }
        return responses;
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
        Document document = refreshTotalPagesIfMissing(findDocument(id));
        return toResponse(document);
    }

    public DocumentViewResponse getViewerInfo(UUID id) {
        Document document = refreshTotalPagesIfMissing(findDocument(id));
        DocumentFile file = documentFileRepository.findAllByDocument_Id(id).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        return DocumentViewResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .description(document.getDescription())
                .documentType(document.getDocumentType())
                .totalPages(document.getTotalPages())
                .fileId(file.getId())
                .fileName(file.getOriginalFileName())
                .mimeType(file.getMimeType())
                .build();
    }

    public DocumentIndexStatusResponse getIndexStatus(UUID id) {
        return documentIndexJobService.getStatus(id);
    }

    @Transactional
    public DocumentResponse update(UUID id, DocumentUpdateRequest request) {
        Document document = findDocument(id);
        String title = request.getTitle().trim();

        if (documentRepository.existsBySubject_IdAndTitleIgnoreCaseAndIdNot(
                document.getSubject().getId(), title, id)) {
            throw new BadRequestException("Document with the same title already exists");
        }

        document.setTitle(title);
        document.setDescription(request.getDescription());
        if (request.getActive() != null) {
            document.setActive(request.getActive());
        }

        documentRepository.save(document);
        return toResponse(document);
    }


    @Transactional
    public DocumentResponse addFiles(UUID id, List<MultipartFile> files) {
        Document document = findDocument(id);
        saveFiles(document, files);
        refreshTotalPages(document);
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
        documentChunkRepository.deleteAllByDocument_Id(id);
        documentIndexJobRepository.deleteAllByDocument_Id(id);
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

    public DocumentFileResource getViewContent(UUID documentId) {
        Document document = refreshTotalPagesIfMissing(findDocument(documentId));
        DocumentFile file = documentFileRepository.findAllByDocument_Id(documentId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        return toFileResource(document, documentId, file);
    }

    public DocumentFileResource getFileViewContent(UUID documentId, UUID fileId) {
        Document document = refreshTotalPagesIfMissing(findDocument(documentId));
        DocumentFile file = documentFileRepository.findByIdAndDocument_Id(fileId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        return toFileResource(document, documentId, file);
    }

    public DocumentFileResource getDownloadContent(UUID documentId) {
        return getViewContent(documentId);
    }

    public DocumentFileResource getFileDownloadContent(UUID documentId, UUID fileId) {
        return getFileViewContent(documentId, fileId);
    }

    private DocumentFileResource toFileResource(Document document, UUID documentId, DocumentFile file) {
        var storedFile = documentStorageService.openReadableFile(documentId, file);
        int totalPages = document.getTotalPages() != null ? document.getTotalPages() : 0;
        return new DocumentFileResource(
                file.getOriginalFileName(),
                file.getMimeType(),
                storedFile.resource(),
                storedFile.fileSize(),
                totalPages
        );
    }

    public record DocumentFileResource(
            String originalFileName,
            String mimeType,
            Resource resource,
            long fileSize,
            int totalPages
    ) {}

    private void validateUploadFiles(List<MultipartFile> files) {
        if (files.isEmpty()) {
            throw new BadRequestException("At least one file is required");
        }
    }

    private List<MultipartFile> getValidFiles(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private List<DocumentUploadRequest> normalizeMetadata(List<DocumentUploadRequest> items, int fileCount) {
        if (items == null || items.isEmpty()) {
            return java.util.stream.IntStream.range(0, fileCount)
                    .mapToObj(i -> new DocumentUploadRequest())
                    .toList();
        }
        if (items.size() != fileCount) {
            throw new BadRequestException("Number of metadata items must match number of files");
        }
        return items;
    }

    private void validateUploadBatch(Subject subject, List<MultipartFile> files, List<DocumentUploadRequest> metadata) {
        Set<String> seenTitles = new HashSet<>();
        Set<String> seenChecksums = new HashSet<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String title = resolveTitle(metadata.get(i).getTitle(), List.of(file));
            String normalizedTitle = title.toLowerCase(Locale.ROOT);

            if (!seenTitles.add(normalizedTitle)) {
                throw new BadRequestException("Duplicate title in upload request: " + title);
            }
            if (documentRepository.existsBySubject_IdAndTitleIgnoreCase(subject.getId(), title)) {
                throw new BadRequestException("Document with the same title already exists: " + title);
            }

            String checksum = documentStorageService.computeChecksum(file);
            if (!seenChecksums.add(checksum)) {
                throw new BadRequestException("Duplicate file content in upload request");
            }
            if (documentFileRepository.existsByChecksum(checksum)) {
                throw new BadRequestException("A file with the same content already exists");
            }
        }
    }

    private User resolveUploadedBy(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(userEmail).orElse(null);
    }

    private void saveFiles(Document document, List<MultipartFile> files) {
        if (files == null) {
            return;
        }

        Set<String> seenChecksums = new HashSet<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            var stored = documentStorageService.store(document.getId(), file);
            if (!seenChecksums.add(stored.checksum())) {
                documentStorageService.deleteFile(stored.filePath());
                throw new BadRequestException("Duplicate file content in upload request");
            }
            if (documentFileRepository.existsByChecksum(stored.checksum())) {
                documentStorageService.deleteFile(stored.filePath());
                throw new BadRequestException("A file with the same content already exists");
            }
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

    private Document refreshTotalPagesIfMissing(Document document) {
        if (document.getTotalPages() != null && document.getTotalPages() > 0) {
            return document;
        }
        return refreshTotalPages(document);
    }

    private Document refreshTotalPages(Document document) {
        int totalPages = documentPageCountService.countDocumentPages(document.getId());
        document.setTotalPages(totalPages);
        return documentRepository.save(document);
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
