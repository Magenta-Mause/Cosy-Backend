package com.magentamause.cosybackend.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * Component keys must match {@code ^[a-zA-Z0-9.\-_]+$} (OpenAPI 3.x components object). The old
     * key "Bearer Authentication" had a space, which the frontend's client generator (orval 8)
     * rejects outright.
     */
    static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(
                        new Components()
                                .addSecuritySchemes(SECURITY_SCHEME_NAME, createSecurityScheme()))
                .info(
                        new Info()
                                .title("Cosy API")
                                .description(
                                        "Management API for Cosy (Cost Optimised Server Yard).")
                                .version("v1.0")
                                .contact(
                                        new Contact()
                                                .name("Cosy Team")
                                                .url("https://github.com/magenta-mause"))
                                .license(
                                        new License()
                                                .name("MIT")
                                                .url("https://opensource.org/licenses/MIT")));
    }

    private SecurityScheme createSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer");
    }
}
