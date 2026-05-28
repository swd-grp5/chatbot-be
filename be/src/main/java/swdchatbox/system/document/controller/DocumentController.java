package swdchatbox.system.document.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import swdchatbox.system.document.dto.request.DocumentFilterRequest;
import swdchatbox.system.document.dto.request.DocumentUpdateRequest;
import swdchatbox.system.document.dto.request.DocumentUploadRequest;
import swdchatbox.system.document.dto.response.DocumentResponse;
import swdchatbox.system.document.enums.DocumentType;
import swdchatbox.system.document.service.DocumentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @Valid @RequestPart("data") DocumentUploadRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(documentService.upload(request, files));
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> getAll(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        DocumentFilterRequest filter = new DocumentFilterRequest();
        filter.setSubjectId(subjectId);
        filter.setDocumentType(documentType);
        filter.setActive(active);

        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(documentService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.findById(id));
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
}
