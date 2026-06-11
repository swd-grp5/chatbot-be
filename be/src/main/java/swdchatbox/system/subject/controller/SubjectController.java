package swdchatbox.system.subject.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.subject.dto.request.SubjectFilterRequest;
import swdchatbox.system.subject.dto.request.SubjectRequest;
import swdchatbox.system.subject.dto.response.SubjectResponse;
import swdchatbox.system.subject.service.SubjectService;

import java.util.UUID;

@RestController
@RequestMapping("/subjects")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SubjectController {

    private final SubjectService subjectService;

    @Operation(summary = "Lấy danh sách môn học", description = "FE dùng để hiển thị list subject có phân trang. Hỗ trợ filter theo active, keyword, sort bằng `sortBy` và `sortDir`.")
    @GetMapping
    public ResponseEntity<PageResponse<SubjectResponse>> findAll(
            @Parameter(description = "Lọc theo trạng thái active") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Từ khóa tìm kiếm theo code, name, description") @RequestParam(required = false) String keyword,
            @Parameter(description = "Trường sắp xếp: code, name, createdAt, updatedAt")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Hướng sắp xếp: asc hoặc desc")
            @RequestParam(required = false) String sortDir,
            @Parameter(description = "Số trang, bắt đầu từ 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số phần tử trên mỗi trang")
            @RequestParam(defaultValue = "20") int size
    ) {
        SubjectFilterRequest filter = new SubjectFilterRequest();
        filter.setActive(active);
        filter.setKeyword(keyword);

        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy, sortDir));
        return ResponseEntity.ok(subjectService.findAll(filter, pageable));
    }

    @Operation(summary = "Lấy chi tiết môn học", description = "FE dùng khi mở trang chi tiết subject hoặc đổ dữ liệu vào form chỉnh sửa.")
    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(subjectService.findById(id));
    }

    @Operation(summary = "Tạo môn học", description = "FE gửi dữ liệu theo `SubjectRequest`. Thường dùng ở màn hình tạo subject mới.")
    @PostMapping
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.ok(subjectService.create(request));
    }

    @Operation(summary = "Cập nhật môn học", description = "FE dùng khi chỉnh sửa thông tin subject đã tồn tại.")
    @PutMapping("/{id}")
    public ResponseEntity<SubjectResponse> update(@PathVariable UUID id, @Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.ok(subjectService.update(id, request));
    }

    @Operation(summary = "Xóa môn học", description = "FE dùng để xóa một subject. Sau khi xóa sẽ trả về HTTP 204.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        subjectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bật/tắt trạng thái môn học", description = "FE dùng để đổi nhanh trạng thái active của subject.")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<SubjectResponse> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(subjectService.toggleActive(id));
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
            case "code", "name", "createdAt", "updatedAt" -> sortBy;
            default -> "createdAt";
        };
    }
}
