package cloud.fiely.common.web

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Serves the React frontend baked into `/static/` and falls back to
 * `index.html` for SPA deep links so client-side routing works on refresh.
 *
 * Rules:
 * - Real files under `/static/` (assets, favicon, index.html) are served as-is.
 * - Requests under the `/api/` or `/actuator/` prefix are never rewritten —
 *   they hit the Spring `@RestController`s (higher precedence than resource
 *   handlers) or return 404 if unmapped.
 * - Requests with a file extension (e.g. `/foo.png`) that don't exist return
 *   404 instead of the SPA shell, so missing assets fail loudly.
 * - Everything else (e.g. `/dashboard`, `/apps/my-app`) is served `index.html`
 *   so React Router can take over.
 */
@Configuration
class SpaWebConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(SpaResourceResolver())
    }
}

private class SpaResourceResolver : PathResourceResolver() {
    private val indexHtml = ClassPathResource("/static/index.html")
    private val apiPrefixes = listOf("api/", "actuator/")

    override fun getResource(resourcePath: String, location: Resource): Resource? {
        // Prefer the real file if it exists on the classpath.
        val resource = super.getResource(resourcePath, location)
        if (resource != null) return resource

        // Never rewrite API routes — let Spring return 404 for unmapped ones.
        if (apiPrefixes.any { resourcePath.startsWith(it) }) return null

        // Anything with an extension that wasn't found is a missing asset — 404.
        if (resourcePath.contains('.')) return null

        // Deep link into the SPA — serve the shell.
        return indexHtml.takeIf { it.exists() }
    }
}
