package com.smartassign.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Developer API documentation at /swagger-ui.html (not linked from the supervisor UI). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartAssignOpenApi() {
        return new OpenAPI()
                .info(new Info().title("SmartAssign API").version("1.0.0")
                        .description("Explainable claim routing. Synthetic dataset - experimental environment."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
