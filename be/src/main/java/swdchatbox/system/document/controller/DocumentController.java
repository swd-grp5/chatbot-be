package swdchatbox.system.document.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import swdchatbox.system.document.dto.request.DocumentFilterRequest;
import swdchatbox.system.document.dto.request.DocumentUpdateRequest;
import swdchatbox.system.document.dto.request.DocumentUploadRequest;
import swdchatbox.system.document.dto.response.DocumentChunkResponse;
import swdchatbox.system.document.dto.response.DocumentDashboardStatsResponse;
import swdchatbox.system.document.dto.response.DocumentIndexStatusResponse;
import swdchatbox.system.document.dto.response.DocumentViewResponse;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.document.dto.response.DocumentResponse;
import swdchatbox.system.document.enums.DocumentType;
import swdchatbox.system.document.service.DocumentIndexingService;
import swdchatbox.system.document.service.DocumentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentIndexingService documentIndexingService;

    @Operation(summary = "Upload tài liệu", description = "FE gửi `multipart/form-data`: `data` là mảng JSON (title, description), mỗi phần tử tương ứng 1 file trong `files`. Không cho trùng title trong subject và không cho trùng nội dung file.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<DocumentResponse>> upload(
            @Valid @RequestPart(value = "data", required = false) List<DocumentUploadRequest> data,
            @Parameter(description = "Danh sách file upload, thứ tự khớp với mảng data")
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication
    ) {
        String userEmail = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(documentService.upload(data, files, userEmail));
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

    @Operation(summary = "Thông tin xem tài liệu", description = "FE dùng để mở màn viewer: lấy title, totalPages, mimeType trước khi gọi `/view` hiển thị file.")
    @GetMapping("/{id}/viewer")
    public ResponseEntity<DocumentViewResponse> viewer(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getViewerInfo(id));
    }

    @Operation(summary = "Xem file tài liệu", description = "FE dùng để hiển thị file trực tiếp trên trình duyệt (PDF viewer). Trả file dạng inline, kết hợp với `totalPages` từ `/viewer`.")
    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> viewDocument(@PathVariable UUID id) {
        return buildFileResponse(documentService.getViewContent(id), false);
    }

    @Operation(summary = "Xem file theo fileId", description = "FE dùng khi document có nhiều file và cần preview đúng file con.")
    @GetMapping("/{documentId}/files/{fileId}/view")
    public ResponseEntity<Resource> viewFile(@PathVariable UUID documentId, @PathVariable UUID fileId) {
        return buildFileResponse(documentService.getFileViewContent(documentId, fileId), false);
    }

    @Operation(summary = "Tải xuống file gốc của tài liệu", description = "FE dùng khi user muốn tải file về máy. Response trả về binary file dạng attachment.")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable UUID id) {
        return buildFileResponse(documentService.getDownloadContent(id), true);
    }

    @Operation(summary = "Tải xuống file theo fileId", description = "FE dùng khi document có nhiều file và muốn tải đúng file con theo `documentId` + `fileId`.")
    @GetMapping("/{documentId}/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID documentId, @PathVariable UUID fileId) {
        return buildFileResponse(documentService.getFileDownloadContent(documentId, fileId), true);
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

    @Operation(summary = "Cập nhật tài liệu", description = "FE gửi JSON để sửa title, description và active. Upload file mới dùng `POST /{id}/files`.")
    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentUpdateRequest request
    ) {
        return ResponseEntity.ok(documentService.update(id, request));
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

    private ResponseEntity<Resource> buildFileResponse(DocumentService.DocumentFileResource file, boolean attachment) {
        Resource resource = new FileSystemResource(file.path());
        String dispositionType = attachment ? "attachment" : "inline";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename=\"" + file.originalFileName() + "\"")
                .header("X-Total-Pages", String.valueOf(file.totalPages()))
                .contentType(resolveMediaType(file.mimeType()))
                .contentLength(file.fileSize())
                .body(resource);
    }

    private MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
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
