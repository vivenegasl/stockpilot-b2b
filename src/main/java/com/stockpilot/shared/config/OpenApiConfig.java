package com.stockpilot.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockPilotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("StockPilot B2B — High-Concurrency Inventory & Order Management API")
                        .description("Mission-critical B2B inventory platform ensuring zero overselling via row-level pessimistic locking, " +
                                "HTTP idempotency, and an immutable append-only ledger.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Víctor Venegas — Software Architect & Lead Full-Stack Engineer")
                                .email("vi.venegasl@duocuc.cl")
                                .url("https://github.com/vivenegasl/stockpilot-b2b"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server"),
                        new Server().url("https://stockpilot.duckdns.org").description("Oracle Cloud Infrastructure (OCI Production)")));
    }
}
