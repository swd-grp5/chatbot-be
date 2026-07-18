package swdchatbox.modules.credit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import swdchatbox.modules.credit.entity.CreditFeatureCost;
import swdchatbox.modules.credit.repository.CreditFeatureCostRepository;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/credit-features")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCreditFeatureController {

    private final CreditFeatureCostRepository featureCostRepository;

    @Operation(summary = "Lấy danh sách tất cả các tính năng AI và giá credit của chúng")
    @GetMapping
    public ResponseEntity<List<CreditFeatureCost>> getAllFeatures() {
        return ResponseEntity.ok(featureCostRepository.findAll());
    }

    @Operation(summary = "Tạo cấu hình giá credit mới cho tính năng AI")
    @PostMapping
    public ResponseEntity<CreditFeatureCost> createFeatureCost(@RequestBody CreditFeatureCost cost) {
        if (featureCostRepository.findByFeatureName(cost.getFeatureName()).isPresent()) {
            throw new BadRequestException("Feature cost configuration already exists for: " + cost.getFeatureName());
        }
        return ResponseEntity.ok(featureCostRepository.save(cost));
    }

    @Operation(summary = "Cập nhật giá credit của một tính năng AI")
    @PutMapping("/{id}")
    public ResponseEntity<CreditFeatureCost> updateFeatureCost(
            @PathVariable UUID id,
            @RequestBody CreditFeatureCost updatedCost
    ) {
        CreditFeatureCost existing = featureCostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feature cost config not found"));

        existing.setFeatureName(updatedCost.getFeatureName());
        existing.setCreditCost(updatedCost.getCreditCost());

        return ResponseEntity.ok(featureCostRepository.save(existing));
    }

    @Operation(summary = "Xóa cấu hình giá credit của tính năng AI")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFeatureCost(@PathVariable UUID id) {
        if (!featureCostRepository.existsById(id)) {
            throw new ResourceNotFoundException("Feature cost config not found");
        }
        featureCostRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
