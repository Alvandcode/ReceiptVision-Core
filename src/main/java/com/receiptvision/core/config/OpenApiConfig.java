package com.receiptvision.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI receiptVisionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReceiptVision Core API")
                        .version("0.2.0")
                        .description("Private per-user receipts. Register at POST /api/auth/register, "
                                + "login at POST /api/auth/login, then send Authorization: Bearer <jwt>."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
