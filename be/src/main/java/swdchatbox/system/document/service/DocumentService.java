package swdchatbox.system.document.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import swdchatbox.system.document.dto.response.DocumentPreviewResponse;
import swdchatbox.system.document.dto.response.DocumentResponse;
import swdchatbox.system.document.dto.response.DocumentViewResponse;
import swdchatbox.system.document.entity.Document;
import swdchatbox.system.document.entity.DocumentFile;
import swdchatbox.system.document.enums.DocumentStatus;
import swdchatbox.system.document.enums.DocumentType;
import swdchatbox.system.document.mapper.DocumentFileMapper;
import swdchatbox.system.document.mapper.DocumentMapper;
import swdchatbox.system.citation.repository.MessageCitationRepository;
import swdchatbox.system.document.entity.DocumentChunk;
import swdchatbox.system.document.repository.DocumentChunkRepository;
import swdchatbox.system.document.repository.DocumentFileRepository;
import swdchatbox.system.document.repository.DocumentIndexJobRepository;
import swdchatbox.system.document.repository.DocumentRepository;
import swdchatbox.system.document.repository.DocumentSpecifications;
import swdchatbox.system.embedding.entity.EmbeddingRecord;
import swdchatbox.system.embedding.repository.EmbeddingRecordRepository;
import swdchatbox.system.embedding.service.VectorStoreService;
import swdchatbox.system.enrollment.service.SubjectEnrollmentService;
import swdchatbox.system.ingestion.repository.IngestionJobRepository;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.subject.entity.Subject;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("\\.[^.\\s]+$");

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentIndexJobRepository documentIndexJobRepository;
    private final EmbeddingRecordRepository embeddingRecordRepository;
    private final MessageCitationRepository messageCitationRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final UserRepository userRepository;
    private final SubjectEnrollmentService subjectEnrollmentService;
    private final DocumentStorageService documentStorageService;
    private final DocumentIndexJobService documentIndexJobService;
    private final DocumentPageCountService documentPageCountService;
    private final DocumentPreviewService documentPreviewService;
    private final VectorStoreService vectorStoreService;

    @Transactional
    public List<DocumentResponse> upload(List<DocumentUploadRequest> items, List<MultipartFile> files, UUID subjectId, String userEmail) {
        List<MultipartFile> validFiles = getValidFiles(files);
        validateUploadFiles(validFiles);

        List<DocumentUploadRequest> metadata = normalizeMetadata(items, validFiles.size());
        List<UUID> resolvedSubjectIds = resolveUploadSubjectIds(subjectId, metadata);
        log.info("[upload] step=validate validFileCount={} userEmail={} subjectCount={}",
                validFiles.size(), userEmail, new LinkedHashSet<>(resolvedSubjectIds).size());

        User uploadedBy = resolveUploader(userEmail);
        Map<UUID, Subject> subjectsById = new HashMap<>();
        for (UUID resolvedSubjectId : new LinkedHashSet<>(resolvedSubjectIds)) {
            Subject subject = subjectEnrollmentService.findActiveSubject(resolvedSubjectId);
            subjectEnrollmentService.requireLecturerCanUpload(uploadedBy, resolvedSubjectId);
            subjectsById.put(resolvedSubjectId, subject);
        }

        validateUploadBatch(resolvedSubjectIds, validFiles, metadata);
        log.info("[upload] step=batch-validated fileCount={} uploadedBy={}", validFiles.size(), userEmail);

        List<DocumentResponse> responses = new ArrayList<>();
        for (int i = 0; i < validFiles.size(); i++) {
            MultipartFile file = validFiles.get(i);
            DocumentUploadRequest item = metadata.get(i);
            Subject subject = subjectsById.get(resolvedSubjectIds.get(i));
            String title = resolveTitle(item.getTitle(), List.of(file));
            log.info("[upload] step=create-document index={} subjectId={} title={} fileName={} fileSize={} contentType={}",
                    i, subject.getId(), title, file.getOriginalFilename(), file.getSize(), file.getContentType());

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
            log.info("[upload] step=document-saved documentId={} title={}", document.getId(), title);
            saveFiles(document, List.of(file));
            refreshTotalPages(document);
            documentIndexJobService.enqueue(document.getId());
            responses.add(toResponse(document));
            log.info("[upload] step=completed documentId={} title={}", document.getId(), title);
        }
        log.info("[upload] step=done uploadedCount={}", responses.size());
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

    public PageResponse<DocumentResponse> findAll(DocumentFilterRequest filter, Pageable pageable, String userEmail) {
        User currentUser = resolveCurrentUser(userEmail);
        Specification<Document> spec = Specification
                .where(DocumentSpecifications.hasSubjectId(filter != null ? filter.getSubjectId() : null))
                .and(DocumentSpecifications.hasSubjectCode(filter != null ? filter.getSubjectCode() : null))
                .and(DocumentSpecifications.hasUploadedById(filter != null ? filter.getUploadedById() : null))
                .and(DocumentSpecifications.uploadedByKeywordLike(filter != null ? filter.getUploadedBy() : null))
                .and(DocumentSpecifications.hasDocumentType(filter != null ? filter.getDocumentType() : null))
                .and(DocumentSpecifications.hasStatus(filter != null ? filter.getStatus() : null))
                .and(DocumentSpecifications.hasActive(filter != null ? filter.getActive() : null))
                .and(DocumentSpecifications.keywordLike(filter != null ? filter.getKeyword() : null))
                .and(DocumentSpecifications.createdAfter(filter != null ? filter.getCreatedFrom() : null))
                .and(DocumentSpecifications.createdBefore(filter != null ? filter.getCreatedTo() : null))
                .and(DocumentSpecifications.subjectIdIn(resolveAccessibleSubjectIds(currentUser)));

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

    public DocumentPreviewResponse getPreview(UUID id) {
        refreshTotalPagesIfMissing(findDocument(id));
        return documentPreviewService.preview(id);
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
        log.info("Deleting document id={}", id);
        findDocument(id);
        List<DocumentFile> documentFiles = documentFileRepository.findAllByDocument_Id(id);

        deleteRelatedVectors(id);
        deleteRelatedDatabaseRecords(id);
        deleteStoredFiles(id, documentFiles);

        documentRepository.deleteById(id);
        log.info("Document deleted id={}", id);
    }

    private void deleteRelatedVectors(UUID documentId) {
        Set<String> vectorIds = new LinkedHashSet<>();
        for (EmbeddingRecord record : embeddingRecordRepository.findAllByChunk_Document_Id(documentId)) {
            if (record.getVectorId() != null && !record.getVectorId().isBlank()) {
                vectorIds.add(record.getVectorId());
            }
        }
        for (DocumentChunk chunk : documentChunkRepository.findAllByDocument_IdOrderByChunkIndexAsc(documentId)) {
            vectorIds.add(chunk.getId().toString());
        }
        vectorStoreService.deleteByIds(new ArrayList<>(vectorIds));
        vectorStoreService.deleteByDocumentId(documentId);
    }

    private void deleteRelatedDatabaseRecords(UUID documentId) {
        messageCitationRepository.deleteAllByDocument_Id(documentId);
        embeddingRecordRepository.deleteAllByChunk_Document_Id(documentId);
        documentChunkRepository.deleteAllByDocument_Id(documentId);
        documentIndexJobRepository.deleteAllByDocument_Id(documentId);
        documentFileRepository.deleteAllByDocument_Id(documentId);
        ingestionJobRepository.deleteByDocument_Id(documentId);
    }

    private void deleteStoredFiles(UUID documentId, List<DocumentFile> documentFiles) {
        log.info("Deleting stored files documentId={} fileCount={}", documentId, documentFiles.size());
        for (DocumentFile file : documentFiles) {
            log.info("Deleting stored file documentId={} fileId={} path={} storedFileName={}",
                    documentId, file.getId(), file.getFilePath(), file.getStoredFileName());
            documentStorageService.deleteFile(file.getFilePath());
        }
        documentStorageService.deleteDocumentFolder(documentId);
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
        log.info("Loading view content documentId={}", documentId);
        Document document = refreshTotalPagesIfMissing(findDocument(documentId));
        DocumentFile file = documentFileRepository.findAllByDocument_Id(documentId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        log.info("View content resolved documentId={} fileId={} path={} mimeType={}",
                documentId, file.getId(), file.getFilePath(), file.getMimeType());
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

    private List<UUID> resolveUploadSubjectIds(UUID defaultSubjectId, List<DocumentUploadRequest> metadata) {
        List<UUID> resolved = new ArrayList<>(metadata.size());
        for (DocumentUploadRequest item : metadata) {
            UUID itemSubjectId = item.getSubjectId() != null ? item.getSubjectId() : defaultSubjectId;
            if (itemSubjectId == null) {
                throw new BadRequestException("Subject is required for each document");
            }
            resolved.add(itemSubjectId);
        }
        return resolved;
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

    private void validateUploadBatch(List<UUID> subjectIds, List<MultipartFile> files, List<DocumentUploadRequest> metadata) {
        Map<UUID, Set<String>> seenTitlesBySubject = new HashMap<>();
        Set<String> seenChecksums = new HashSet<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            UUID subjectId = subjectIds.get(i);
            String title = resolveTitle(metadata.get(i).getTitle(), List.of(file));
            String normalizedTitle = title.toLowerCase(Locale.ROOT);

            Set<String> seenTitles = seenTitlesBySubject.computeIfAbsent(subjectId, ignored -> new HashSet<>());
            if (!seenTitles.add(normalizedTitle)) {
                throw new BadRequestException("Duplicate title in upload request for the same subject: " + title);
            }
            if (documentRepository.existsBySubject_IdAndTitleIgnoreCase(subjectId, title)) {
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

    private User resolveUploader(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new BadRequestException("Authenticated user is required to upload documents");
        }
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private User resolveCurrentUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(userEmail).orElse(null);
    }

    private List<UUID> resolveAccessibleSubjectIds(User user) {
        if (user == null || user.getRole() == null) {
            return null;
        }
        String roleCode = user.getRole().getCode();
        if (RoleCodes.ADMIN.equals(roleCode)) {
            return null;
        }
        if (RoleCodes.LECTURER.equals(roleCode)) {
            return subjectEnrollmentService.getLecturerSubjectIds(user.getId());
        }
        if (RoleCodes.STUDENT.equals(roleCode)) {
            return subjectEnrollmentService.getStudentSubjectIds(user.getId());
        }
        return List.of();
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
            log.info("[upload] step=saveFiles documentId={} fileName={} fileSize={} contentType={}",
                    document.getId(), file.getOriginalFilename(), file.getSize(), file.getContentType());
            var stored = documentStorageService.store(document.getId(), file);
            log.info("[upload] step=store-succeeded documentId={} storedFileName={} filePath={} checksum={}",
                    document.getId(), stored.storedFileName(), stored.filePath(), stored.checksum());
            if (!seenChecksums.add(stored.checksum())) {
                log.warn("[upload] step=duplicate-in-batch documentId={} checksum={}", document.getId(), stored.checksum());
                documentStorageService.deleteFile(stored.filePath());
                throw new BadRequestException("Duplicate file content in upload request");
            }
            if (documentFileRepository.existsByChecksum(stored.checksum())) {
                log.warn("[upload] step=duplicate-in-db documentId={} checksum={}", document.getId(), stored.checksum());
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
            log.info("[upload] step=file-record-saved documentId={} fileId={}", document.getId(), documentFile.getId());
        }
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
