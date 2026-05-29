package swdchatbox.system.subscription.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.subscription.dto.response.StudentSubscriptionResponse;
import swdchatbox.system.subscription.service.StudentSubscriptionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StudentSubscriptionController {

    private final StudentSubscriptionService subscriptionService;

    @PostMapping("/subjects/{subjectId}")
    public ResponseEntity<StudentSubscriptionResponse> subscribe(
            @PathVariable UUID subjectId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(subscriptionService.subscribe(subjectId, authentication.getName()));
    }

    @DeleteMapping("/subjects/{subjectId}")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable UUID subjectId,
            Authentication authentication
    ) {
        subscriptionService.unsubscribe(subjectId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<List<StudentSubscriptionResponse>> mySubscriptions(
            @RequestParam(required = false) Boolean active,
            Authentication authentication
    ) {
        return ResponseEntity.ok(subscriptionService.findMySubscriptions(authentication.getName(), active));
    }
}

