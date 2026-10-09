package com.edifika.payment.shared.infrastructure.documentation.openapi.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de la documentación OpenAPI (Swagger UI) del microservicio,
 * incluido el esquema de seguridad Bearer JWT para probar los endpoints protegidos.
 */
@Configuration
public class OpenApiConfiguration {

    @Value("${server.port}")
    private String serverPort;

    @Bean
    public OpenAPI edifikaPlatformOpenApi() {

        // General configuration
        var openApi = new OpenAPI();

        openApi
                .info(new Info()
                        .title("Edifika API- Payment Microservice")
                        .description("Backend API for Edifika - A smart building and condominium management platform developed by Condomia Startup")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Condomia Development Team")
                                .email("dev@edifika.com")
                                .url("https://edifika.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("Edifika Platform Documentation")
                        .url("https://docs.edifika.com"))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Servidor local de desarrollo")
                ));

        // Add security scheme
        final String securitySchemeName = "bearerAuth";

        openApi.addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));

        // Return the OpenAPI configuration object with all the settings
        return openApi;
    }
}
