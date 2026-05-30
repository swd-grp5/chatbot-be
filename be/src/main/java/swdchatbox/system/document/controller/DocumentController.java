package swdchatbox.system.document.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import swdchatbox.system.document.dto.request.DocumentFilterRequest;
import swdchatbox.system.document.dto.request.DocumentUpdateRequest;
import swdchatbox.system.document.dto.request.DocumentUploadRequest;
import swdchatbox.system.document.dto.response.DocumentChunkResponse;
import swdchatbox.system.document.dto.response.DocumentDashboardStatsResponse;
import swdchatbox.system.document.dto.response.DocumentIndexStatusResponse;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.document.dto.response.DocumentResponse;
import swdchatbox.system.document.entity.DocumentFile;
import swdchatbox.system.document.enums.DocumentType;
import swdchatbox.system.document.repository.DocumentFileRepository;
import swdchatbox.system.document.service.DocumentIndexingService;
import swdchatbox.system.document.service.DocumentService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentIndexingService documentIndexingService;
    private final DocumentFileRepository documentFileRepository;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(documentService.upload(new DocumentUploadRequest(), files));
    }

    @GetMapping("/stats")
    public ResponseEntity<DocumentDashboardStatsResponse> stats() {
        return ResponseEntity.ok(documentService.getStats());
    }

    @GetMapping("/{id}/index-status")
    public ResponseEntity<DocumentIndexStatusResponse> indexStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getIndexStatus(id));
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<List<DocumentChunkResponse>> chunks(@PathVariable UUID id) {
        return ResponseEntity.ok(documentIndexingService.getChunks(id).stream().map(swdchatbox.system.document.mapper.DocumentChunkMapper::toResponse).toList());
    }

    @GetMapping
    public ResponseEntity<PageResponse<DocumentResponse>> getAll(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) swdchatbox.system.document.enums.DocumentStatus status,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        DocumentFilterRequest filter = new DocumentFilterRequest();
        filter.setSubjectId(subjectId);
        filter.setDocumentType(documentType);
        filter.setStatus(status);
        filter.setActive(active);
        filter.setKeyword(keyword);
        filter.setCreatedFrom(createdFrom != null ? java.time.LocalDateTime.parse(createdFrom) : null);
        filter.setCreatedTo(createdTo != null ? java.time.LocalDateTime.parse(createdTo) : null);

        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy, sortDir));
        return ResponseEntity.ok(documentService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.findById(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID id) throws Exception {
        DocumentFile file = documentFileRepository.findAllByDocument_Id(id).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("File not found"));
        byte[] content = Files.readAllBytes(Path.of(file.getFilePath()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getOriginalFileName() + "\"")
                .contentType(MediaType.parseMediaType(file.getMimeType() != null ? file.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .body(content);
    }

    @GetMapping("/{documentId}/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable UUID documentId, @PathVariable UUID fileId) throws Exception {
        DocumentFile file = documentFileRepository.findById(fileId)
                .filter(f -> f.getDocument() != null && f.getDocument().getId().equals(documentId))
                .orElseThrow(() -> new RuntimeException("File not found"));
        byte[] content = Files.readAllBytes(Path.of(file.getFilePath()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getOriginalFileName() + "\"")
                .contentType(MediaType.parseMediaType(file.getMimeType() != null ? file.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .body(content);
    }

    @PostMapping("/{id}/reindex")
    public ResponseEntity<DocumentResponse> reindex(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.reindex(id));
    }

    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<DocumentResponse> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.toggleActive(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestPart("data") DocumentUpdateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(documentService.update(id, request, files));
    }

    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> addFiles(
            @PathVariable UUID id,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.ok(documentService.addFiles(id, files));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String property = normalizeSortProperty(sortBy);
        String direction = sortDir != null ? sortDir : "desc";
        return "asc".equalsIgnoreCase(direction) ? Sort.by(property).ascending() : Sort.by(property).descending();
    }

    private String normalizeSortProperty(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }
        return switch (sortBy) {
            case "title", "status", "documentType", "createdAt", "updatedAt" -> sortBy;
            default -> "createdAt";
        };
    }
}
