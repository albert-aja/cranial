package io.cranial.platform.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SCHEME_APP_API_KEY = "AppApiKey";
    public static final String SCHEME_ADMIN_JWT = "AdminBearerJwt";

    @Bean
    public OpenAPI cranialOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CRANIAL — Universal AI Integration Platform API")
                        .version("v1")
                        .description("One Brain. Many Connections. Standalone model-independent and local-first AI integration, orchestration, and governance platform."))
                .components(new Components()
                        .addSecuritySchemes(SCHEME_APP_API_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Cranial-Api-Key")
                                .description("Application API credential (`crn_<env>_<prefix>.<secret>`)"))
                        .addSecuritySchemes(SCHEME_ADMIN_JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Platform operator JWT issued by `/api/admin/v1/auth/login`")));
    }

    @Bean
    public GroupedOpenApi integrationV1Api() {
        return GroupedOpenApi.builder()
                .group("integration-v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminV1Api() {
        return GroupedOpenApi.builder()
                .group("admin-v1")
                .pathsToMatch("/api/admin/v1/**")
                .build();
    }
}

