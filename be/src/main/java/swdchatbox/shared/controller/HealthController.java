package swdchatbox.shared.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swdchatbox.shared.dto.HealthCheckResponse;
import swdchatbox.shared.service.HealthCheckService;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class HealthController {

    private final HealthCheckService healthCheckService;

    @GetMapping("/health")
    public ResponseEntity<HealthCheckResponse> health() {
        return ResponseEntity.ok(healthCheckService.check());
    }
}
