package swdchatbox.system.common.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record HealthCheckResponse(
        String status,
        Instant timestamp,
        ServiceHealth backend,
        ServiceHealth database,
        List<ServiceHealth> services
) {
    @Builder
    public record ServiceHealth(
            String name,
            String status,
            String detail
    ) {
    }
}
