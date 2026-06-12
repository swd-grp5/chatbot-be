package swdchatbox.system.lecturer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.subject.dto.response.SubjectSummaryResponse;
import swdchatbox.system.lecturer.dto.request.LecturerFilterRequest;
import swdchatbox.system.lecturer.dto.request.LecturerRequest;
import swdchatbox.system.lecturer.dto.request.LecturerUpdateRequest;
import swdchatbox.system.lecturer.dto.response.LecturerResponse;
import swdchatbox.system.lecturer.service.LecturerService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/lecturers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class LecturerController {

    private final LecturerService lecturerService;

    @Operation(summary = "Lấy danh sách giảng viên", description = "FE dùng để hiển thị list lecturer có phân trang. Hỗ trợ filter theo active, keyword, khoảng thời gian, sort bằng `sortBy` và `sortDir`.")
    @GetMapping
    public ResponseEntity<PageResponse<LecturerResponse>> findAll(
            @Parameter(description = "Lọc theo trạng thái active") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Từ khóa tìm kiếm theo fullName, email") @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo môn đã gán") @RequestParam(required = false) UUID subjectId,
            @Parameter(description = "Ngày tạo từ (ISO-8601)") @RequestParam(required = false) String createdFrom,
            @Parameter(description = "Ngày tạo đến (ISO-8601)") @RequestParam(required = false) String createdTo,
            @Parameter(description = "Trường sắp xếp: fullName, email, active, createdAt, updatedAt")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Hướng sắp xếp: asc hoặc desc")
            @RequestParam(required = false) String sortDir,
            @Parameter(description = "Số trang, bắt đầu từ 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số phần tử trên mỗi trang")
            @RequestParam(defaultValue = "20") int size
    ) {
        LecturerFilterRequest filter = new LecturerFilterRequest();
        filter.setActive(active);
        filter.setKeyword(keyword);
        filter.setSubjectId(subjectId);
        filter.setCreatedFrom(createdFrom != null ? java.time.LocalDateTime.parse(createdFrom) : null);
        filter.setCreatedTo(createdTo != null ? java.time.LocalDateTime.parse(createdTo) : null);

        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy, sortDir));
        return ResponseEntity.ok(lecturerService.findAll(filter, pageable));
    }

    @Operation(summary = "Lấy danh sách môn được phép upload", description = "Giảng viên đang đăng nhập xem các môn mình được gán để chọn khi upload tài liệu.")
    @GetMapping("/my-subjects")
    public ResponseEntity<List<SubjectSummaryResponse>> mySubjects(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(lecturerService.findMyUploadSubjects(email));
    }

    @Operation(summary = "Lấy chi tiết giảng viên", description = "FE dùng khi mở trang chi tiết lecturer hoặc đổ dữ liệu vào form chỉnh sửa.")
    @GetMapping("/{id}")
    public ResponseEntity<LecturerResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(lecturerService.findById(id));
    }

    @Operation(summary = "Tạo giảng viên", description = "Admin tạo tài khoản lecturer mới theo `LecturerRequest`.")
    @PostMapping
    public ResponseEntity<LecturerResponse> create(@Valid @RequestBody LecturerRequest request) {
        return ResponseEntity.ok(lecturerService.create(request));
    }

    @Operation(summary = "Cập nhật giảng viên", description = """
            Admin chỉnh sửa thông tin lecturer. `subjectIds` toggle từng môn: ID chưa gán thì thêm, ID đã gán thì gỡ.
            Chỉ cần gửi môn cần đổi (vd. thêm 1 môn: `["uuid-moi"]`, gỡ 1 môn: `["uuid-cu"]`). Sau cập nhật phải còn ít nhất 1 môn.""")
    @PutMapping("/{id}")
    public ResponseEntity<LecturerResponse> update(@PathVariable UUID id, @RequestBody LecturerUpdateRequest request) {
        return ResponseEntity.ok(lecturerService.update(id, request));
    }

    @Operation(summary = "Xóa giảng viên", description = "Admin xóa lecturer. Không thể xóa lecturer đã upload tài liệu.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        lecturerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bật/tắt trạng thái giảng viên", description = "Admin đổi nhanh trạng thái active của lecturer.")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<LecturerResponse> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(lecturerService.toggleActive(id));
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
            case "fullName", "email", "createdAt", "updatedAt" -> sortBy;
            case "active" -> "isActive";
            default -> "createdAt";
        };
    }
}
