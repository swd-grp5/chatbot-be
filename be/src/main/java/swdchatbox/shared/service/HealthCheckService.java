package swdchatbox.shared.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import swdchatbox.shared.dto.HealthCheckResponse;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.health.services:}")
    private List<String> serviceUrls;

    public HealthCheckResponse check() {
        HealthCheckResponse.ServiceHealth backend = HealthCheckResponse.ServiceHealth.builder()
                .name("backend")
                .status("UP")
                .detail("Application is running")
                .build();

        HealthCheckResponse.ServiceHealth database = checkDatabase();
        List<HealthCheckResponse.ServiceHealth> services = new ArrayList<>();
        if (serviceUrls != null) {
            for (String url : serviceUrls) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                services.add(checkService(url.trim()));
            }
        }

        String overall = ("UP".equals(database.status()) && services.stream().allMatch(s -> "UP".equals(s.status())))
                ? "UP"
                : "DEGRADED";

        return HealthCheckResponse.builder()
                .status(overall)
                .timestamp(Instant.now())
                .backend(backend)
                .database(database)
                .services(services)
                .build();
    }

    private HealthCheckResponse.ServiceHealth checkDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(result)) {
                return HealthCheckResponse.ServiceHealth.builder()
                        .name("database")
                        .status("UP")
                        .detail("Database query succeeded")
                        .build();
            }
            return HealthCheckResponse.ServiceHealth.builder()
                    .name("database")
                    .status("DEGRADED")
                    .detail("Unexpected database response")
                    .build();
        } catch (Exception ex) {
            return HealthCheckResponse.ServiceHealth.builder()
                    .name("database")
                    .status("DOWN")
                    .detail(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .build();
        }
    }

    private HealthCheckResponse.ServiceHealth checkService(String url) {
        try {
            RestClient.create()
                    .get()
                    .uri(URI.create(url))
                    .retrieve()
                    .toBodilessEntity();
            return HealthCheckResponse.ServiceHealth.builder()
                    .name(url)
                    .status("UP")
                    .detail("Service responded successfully")
                    .build();
        } catch (Exception ex) {
            return HealthCheckResponse.ServiceHealth.builder()
                    .name(url)
                    .status("DOWN")
                    .detail(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .build();
        }
    }
}
