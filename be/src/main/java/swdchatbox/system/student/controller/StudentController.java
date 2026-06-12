package swdchatbox.system.student.controller;

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
import swdchatbox.system.student.dto.request.StudentFilterRequest;
import swdchatbox.system.student.dto.request.StudentRequest;
import swdchatbox.system.student.dto.request.StudentUpdateRequest;
import swdchatbox.system.student.dto.response.StudentResponse;
import swdchatbox.system.student.service.StudentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

    private final StudentService studentService;

    @Operation(summary = "Lấy danh sách sinh viên", description = "FE dùng để hiển thị list student có phân trang. Hỗ trợ filter theo active, keyword, khoảng thời gian, sort bằng `sortBy` và `sortDir`.")
    @GetMapping
    public ResponseEntity<PageResponse<StudentResponse>> findAll(
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
        StudentFilterRequest filter = new StudentFilterRequest();
        filter.setActive(active);
        filter.setKeyword(keyword);
        filter.setSubjectId(subjectId);
        filter.setCreatedFrom(createdFrom != null ? java.time.LocalDateTime.parse(createdFrom) : null);
        filter.setCreatedTo(createdTo != null ? java.time.LocalDateTime.parse(createdTo) : null);

        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy, sortDir));
        return ResponseEntity.ok(studentService.findAll(filter, pageable));
    }

    @Operation(summary = "Lấy danh sách môn được gán", description = "Sinh viên đang đăng nhập xem các môn mình được gán để chat.")
    @GetMapping("/my-subjects")
    public ResponseEntity<List<SubjectSummaryResponse>> mySubjects(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(studentService.findMySubjects(email));
    }

    @Operation(summary = "Lấy chi tiết sinh viên", description = "FE dùng khi mở trang chi tiết student hoặc đổ dữ liệu vào form chỉnh sửa.")
    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(studentService.findById(id));
    }

    @Operation(summary = "Tạo sinh viên", description = "Admin tạo tài khoản student mới theo `StudentRequest`.")
    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest request) {
        return ResponseEntity.ok(studentService.create(request));
    }

    @Operation(summary = "Cập nhật sinh viên", description = """
            Admin chỉnh sửa thông tin student. `subjectIds` toggle từng môn: ID chưa gán thì thêm, ID đã gán thì gỡ.
            Chỉ cần gửi môn cần đổi (vd. thêm 1 môn: `["uuid-moi"]`, gỡ 1 môn: `["uuid-cu"]`). Sau cập nhật phải còn ít nhất 1 môn.""")
    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> update(@PathVariable UUID id, @RequestBody StudentUpdateRequest request) {
        return ResponseEntity.ok(studentService.update(id, request));
    }

    @Operation(summary = "Xóa sinh viên", description = "Admin xóa student. Không thể xóa student đang có subscription hoặc hội thoại chat.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        studentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bật/tắt trạng thái sinh viên", description = "Admin đổi nhanh trạng thái active của student.")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<StudentResponse> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(studentService.toggleActive(id));
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
