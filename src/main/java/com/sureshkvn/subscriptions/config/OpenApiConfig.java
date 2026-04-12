package com.sureshkvn.subscriptions.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger configuration.
 *
 * <p>Access the interactive docs at: <a href="http://localhost:8080/api/swagger-ui.html">
 * http://localhost:8080/api/swagger-ui.html</a>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI subscriptionsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Subscriptions API")
                        .description("""
                                Flexible subscription management API supporting recurring billing
                                from hourly intervals to monthly and custom cycles.

                                ## Features
                                - Plan management (create, update, archive)
                                - Subscription lifecycle (create, activate, pause, cancel)
                                - Billing cycle tracking
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Suresh")
                                .email("sureshkvn@gmail.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080/api").description("Local Development"),
                        new Server().url("https://api.subscriptions.example.com").description("Production")
                ));
    }
}
