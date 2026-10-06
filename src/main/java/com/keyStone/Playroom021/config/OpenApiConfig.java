package com.keyStone.Playroom021.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for Swagger UI. Declares a JWT bearer scheme so the "Authorize" button
 * in Swagger UI can send {@code Authorization: Bearer <token>} (token from POST /api/auth/login).
 * Documentation only: authorization is still enforced by SecurityConfig and the services.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI keystoneOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("KEYSTONE API")
                        .version("1.0.0")
                        .description("Field-service work order management: customers, sites, work orders, "
                                + "parts/inventory, time logs and SLA tracking. Roles: MANAGER, DISPATCHER, "
                                + "TECHNICIAN, CUSTOMER."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
