package swdchatbox.modules.subscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.modules.subscription.dto.response.UserSubscriptionResponse;
import swdchatbox.modules.subscription.entity.SubscriptionPlan;
import swdchatbox.modules.subscription.service.UserSubscriptionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserSubscriptionController {

    private final UserSubscriptionService subscriptionService;

    @Operation(summary = "Lấy thông tin gói cước hiện tại của tôi")
    @GetMapping("/current")
    public ResponseEntity<SubscriptionPlan> getCurrentPlan(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.getCurrentUserPlan(authentication.getName()));
    }

    @Operation(summary = "Đăng ký gói cước VIP hệ thống")
    @PostMapping("/subscribe/{planId}")
    public ResponseEntity<UserSubscriptionResponse> subscribe(
            @Parameter(description = "ID của gói cước cần đăng ký") @PathVariable UUID planId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(subscriptionService.subscribe(planId, authentication.getName()));
    }

    @Operation(summary = "Hủy gói cước VIP hiện tại")
    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(Authentication authentication) {
        subscriptionService.unsubscribe(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Xem lịch sử mua gói cước của tôi")
    @GetMapping("/my-history")
    public ResponseEntity<List<UserSubscriptionResponse>> getMyHistory(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.findMySubscriptionHistory(authentication.getName()));
    }

    @Operation(summary = "[Admin] Lấy danh sách gói kèm Phân trang & Sắp xếp (Sort)")
    @GetMapping("/admin/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<SubscriptionPlan>> getAllPlansForAdmin(
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số lượng bản ghi trên một trang") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Cột cần sắp xếp (ví dụ: price, name, createdAt)") @RequestParam(defaultValue = "price") String sortBy,
            @Parameter(description = "Hướng sắp xếp (asc: tăng dần, desc: giảm dần)") @RequestParam(defaultValue = "asc") String sortDir
    ) {
        Page<SubscriptionPlan> plans = subscriptionService.getAllPlansForAdmin(page, size, sortBy, sortDir);
        return ResponseEntity.ok(plans);
    }

    @Operation(summary = "[Admin] Tạo mới một gói cước")
    @PostMapping("/admin/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionPlan> createPlan(@RequestBody SubscriptionPlan plan) {
        return ResponseEntity.ok(subscriptionService.createPlan(plan));
    }

    @Operation(summary = "[Admin] Cập nhật thông tin gói cước")
    @PutMapping("/admin/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionPlan> updatePlan(
            @Parameter(description = "ID của gói cước cần sửa") @PathVariable UUID id,
            @RequestBody SubscriptionPlan plan
    ) {
        return ResponseEntity.ok(subscriptionService.updatePlan(id, plan));
    }

    @Operation(summary = "[Admin] Xóa một gói cước")
    @DeleteMapping("/admin/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePlan(
            @Parameter(description = "ID của gói cước cần xóa") @PathVariable UUID id
    ) {
        subscriptionService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }
}
