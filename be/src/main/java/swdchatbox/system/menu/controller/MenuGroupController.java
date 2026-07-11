package swdchatbox.system.menu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.menu.dto.request.CreateMenuGroupRequest;
import swdchatbox.system.menu.dto.request.UpdateMenuGroupRequest;
import swdchatbox.system.menu.dto.response.MenuGroupResponse;
import swdchatbox.system.menu.service.MenuGroupService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/menu-groups")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MenuGroupController {

    private final MenuGroupService menuGroupService;

    @Operation(summary = "Lấy danh sách menu group", description = "Admin: danh sách group theo displayOrder, không kèm items.")
    @GetMapping
    public ResponseEntity<List<MenuGroupResponse>> findAll() {
        return ResponseEntity.ok(menuGroupService.findAll());
    }

    @Operation(summary = "Lấy danh sách menu group kèm items", description = "Admin: dùng cho màn quản lý sidebar.")
    @GetMapping("/details")
    public ResponseEntity<List<MenuGroupResponse>> findAllWithItems() {
        return ResponseEntity.ok(menuGroupService.findAllWithItems());
    }

    @Operation(summary = "Lấy chi tiết menu group", description = "Admin: chi tiết group kèm items.")
    @GetMapping("/{id}")
    public ResponseEntity<MenuGroupResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(menuGroupService.findById(id));
    }

    @Operation(summary = "Tạo menu group")
    @PostMapping
    public ResponseEntity<MenuGroupResponse> create(@Valid @RequestBody CreateMenuGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuGroupService.create(request));
    }

    @Operation(summary = "Cập nhật menu group", description = "Chỉ gửi các field muốn đổi.")
    @PutMapping("/{id}")
    public ResponseEntity<MenuGroupResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateMenuGroupRequest request
    ) {
        return ResponseEntity.ok(menuGroupService.update(id, request));
    }

    @Operation(summary = "Xóa menu group", description = "Xóa group và toàn bộ items thuộc group.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        menuGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bật/tắt menu group")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<MenuGroupResponse> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(menuGroupService.toggleActive(id));
    }
}
