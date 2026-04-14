package cloud.fiely.common.web

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI / Swagger bootstrap.
 *
 * Springdoc scans Spring MVC controllers on the classpath — both the ones
 * defined in `fiely-core` and any registered by PF4J plugins that extend
 * the MVC request mappings — and builds the spec from there. This bean
 * only adds the top-level metadata and a shared `bearerAuth` security
 * scheme that matches the `Authorization: Bearer <jwt>` header used by
 * the auth endpoints.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun fielyOpenApi(
        @Value("\${spring.application.name:fiely-backend}") appName: String,
    ): OpenAPI {
        val bearerSchemeName = "bearerAuth"
        return OpenAPI()
            .info(
                Info()
                    .title("Fiely API")
                    .description(
                        "REST API for Fiely — a pluggable file and knowledge platform. " +
                            "Endpoints are contributed both by the core application ($appName) " +
                            "and by loaded PF4J plugins.",
                    )
                    .version("0.0.1-SNAPSHOT")
                    .license(
                        License()
                            .name("Apache-2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0"),
                    ),
            )
            .addSecurityItem(SecurityRequirement().addList(bearerSchemeName))
            .components(
                Components().addSecuritySchemes(
                    bearerSchemeName,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT issued by the active auth provider (see POST /api/auth/login)."),
                ),
            )
    }
}
