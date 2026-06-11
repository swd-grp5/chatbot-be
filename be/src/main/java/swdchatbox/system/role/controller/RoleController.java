package swdchatbox.system.role.controller;

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
import swdchatbox.system.role.dto.request.RoleRequest;
import swdchatbox.system.role.dto.request.RoleUpdateRequest;
import swdchatbox.system.role.dto.response.RoleResponse;
import swdchatbox.system.role.service.RoleService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "Lấy danh sách role", description = "FE dùng để hiển thị list role có phân trang. Hỗ trợ sort bằng `sortBy` và `sortDir`.")
    @GetMapping
    public ResponseEntity<PageResponse<RoleResponse>> findAll(
            @Parameter(description = "Trường sắp xếp: code, name, createdAt, updatedAt")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Hướng sắp xếp: asc hoặc desc")
            @RequestParam(required = false) String sortDir,
            @Parameter(description = "Số trang, bắt đầu từ 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số phần tử trên mỗi trang")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy, sortDir));
        return ResponseEntity.ok(roleService.findAll(pageable));
    }

    @Operation(summary = "Lấy danh sách role đang active", description = "FE dùng khi cần dropdown chọn role (ví dụ gán role cho user).")
    @GetMapping("/active")
    public ResponseEntity<List<RoleResponse>> findAllActive() {
        return ResponseEntity.ok(roleService.findAllActive());
    }

    @Operation(summary = "Lấy chi tiết role", description = "FE dùng khi mở trang chi tiết role hoặc đổ dữ liệu vào form chỉnh sửa.")
    @GetMapping("/{id}")
    public ResponseEntity<RoleResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.findById(id));
    }

    @Operation(summary = "Tạo role", description = "Admin tạo role mới theo `RoleRequest`.")
    @PostMapping
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.create(request));
    }

    @Operation(summary = "Cập nhật role", description = "Admin chỉnh sửa thông tin role đã tồn tại. Chỉ cần gửi các field muốn đổi; field không gửi sẽ giữ nguyên giá trị cũ.")
    @PutMapping("/{id}")
    public ResponseEntity<RoleResponse> update(@PathVariable UUID id, @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(roleService.update(id, request));
    }

    @Operation(summary = "Xóa role", description = "Admin xóa role. Không thể xóa role hệ thống hoặc role đang được gán cho user.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bật/tắt trạng thái role", description = "Admin đổi nhanh trạng thái active của role.")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<RoleResponse> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.toggleActive(id));
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
