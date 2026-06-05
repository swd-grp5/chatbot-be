package swdchatbox.system.document.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentIndexingService documentIndexingService;
    private final DocumentFileRepository documentFileRepository;

    @Operation(summary = "Upload tài liệu", description = "FE gửi file dạng `multipart/form-data` ở field `files`. Dùng để tạo document mới.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @Parameter(description = "Danh sách file upload")
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(documentService.upload(new DocumentUploadRequest(), files));
    }

    @Operation(summary = "Thống kê tài liệu", description = "FE dùng để hiển thị số liệu dashboard như tổng document, ready, processing, failed.")
    @GetMapping("/stats")
    public ResponseEntity<DocumentDashboardStatsResponse> stats() {
        return ResponseEntity.ok(documentService.getStats());
    }

    @Operation(summary = "Lấy trạng thái index", description = "FE dùng để kiểm tra document đã được index hay chưa trước khi hiển thị nội dung search/chunk.")
    @GetMapping("/{id}/index-status")
    public ResponseEntity<DocumentIndexStatusResponse> indexStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getIndexStatus(id));
    }

    @Operation(summary = "Lấy danh sách chunks", description = "FE dùng để xem các đoạn nội dung đã index của document.")
    @GetMapping("/{id}/chunks")
    public ResponseEntity<List<DocumentChunkResponse>> chunks(@PathVariable UUID id) {
        return ResponseEntity.ok(documentIndexingService.getChunks(id).stream().map(swdchatbox.system.document.mapper.DocumentChunkMapper::toResponse).toList());
    }

    @Operation(summary = "Lấy danh sách tài liệu", description = "FE dùng để render bảng document. Hỗ trợ filter theo subject, loại tài liệu, trạng thái, active, keyword, khoảng thời gian và phân trang.")
    @GetMapping
    public ResponseEntity<PageResponse<DocumentResponse>> getAll(
            @Parameter(description = "Lọc theo subjectId") @RequestParam(required = false) UUID subjectId,
            @Parameter(description = "Lọc theo loại tài liệu") @RequestParam(required = false) DocumentType documentType,
            @Parameter(description = "Lọc theo trạng thái index") @RequestParam(required = false) swdchatbox.system.document.enums.DocumentStatus status,
            @Parameter(description = "Lọc theo trạng thái active") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Từ khóa tìm kiếm") @RequestParam(required = false) String keyword,
            @Parameter(description = "Ngày tạo từ (ISO-8601)") @RequestParam(required = false) String createdFrom,
            @Parameter(description = "Ngày tạo đến (ISO-8601)") @RequestParam(required = false) String createdTo,
            @Parameter(description = "Số trang, bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số phần tử trên mỗi trang") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Trường sắp xếp: title, status, documentType, createdAt, updatedAt") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Hướng sắp xếp: asc hoặc desc") @RequestParam(required = false) String sortDir
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

    @Operation(summary = "Lấy chi tiết tài liệu", description = "FE dùng khi mở trang chi tiết document hoặc đổ dữ liệu vào form chỉnh sửa.")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.findById(id));
    }

    @Operation(summary = "Tải xuống file gốc của tài liệu", description = "FE dùng để tải file đầu tiên/gốc của document. Response trả về binary file.")
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

    @Operation(summary = "Tải xuống file theo fileId", description = "FE dùng khi document có nhiều file và muốn tải đúng file con theo `documentId` + `fileId`.")
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

    @Operation(summary = "Index lại tài liệu", description = "FE dùng khi cần reprocess/reindex document sau upload hoặc sau khi cập nhật file.")
    @PostMapping("/{id}/reindex")
    public ResponseEntity<DocumentResponse> reindex(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.reindex(id));
    }

    @Operation(summary = "Bật/tắt tài liệu", description = "FE dùng để đổi nhanh trạng thái active của document.")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<DocumentResponse> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.toggleActive(id));
    }

    @Operation(summary = "Cập nhật tài liệu", description = "FE gửi dữ liệu cập nhật ở field `data` và file mới ở field `files` nếu có. Request là multipart/form-data.")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestPart("data") DocumentUpdateRequest request,
            @Parameter(description = "Danh sách file mới") @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(documentService.update(id, request, files));
    }

    @Operation(summary = "Thêm file vào tài liệu", description = "FE dùng khi document đã có sẵn và muốn upload thêm file con. Gửi multipart/form-data ở field `files`.")
    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> addFiles(
            @PathVariable UUID id,
            @Parameter(description = "Danh sách file cần thêm") @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.ok(documentService.addFiles(id, files));
    }

    @Operation(summary = "Xóa tài liệu", description = "FE dùng để xóa document theo id. Sau khi xóa sẽ trả về HTTP 204.")
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
