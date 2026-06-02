package swdchatbox.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SWDChatBox API",
                version = "v1"
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI(
            @Value("${app.public-url:}") String publicUrl,
            @Value("${server.port:8080}") String serverPort
    ) {
        List<Server> servers = new ArrayList<>();

        String localUrl = "http://localhost:" + serverPort;
        servers.add(new Server().url(localUrl).description("Local development"));

        if (publicUrl != null && !publicUrl.isBlank()) {
            String deployedUrl = publicUrl.trim();
            if (!localUrl.equalsIgnoreCase(deployedUrl)) {
                servers.add(new Server().url(deployedUrl).description("Deployed backend"));
            }
        }

        return new OpenAPI().servers(servers);
    }
}

