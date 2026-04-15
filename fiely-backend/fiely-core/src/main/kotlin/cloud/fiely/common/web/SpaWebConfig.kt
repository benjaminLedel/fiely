package cloud.fiely.common.web

import org.springframework.boot.autoconfigure.web.WebProperties
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Serves the React frontend and falls back to `index.html` for SPA deep
 * links so client-side routing works on refresh.
 *
 * Static locations are taken from Spring Boot's own
 * `spring.web.resources.static-locations` so operators can plug in extra
 * paths without patching code. The in-container build bakes the frontend
 * under `classpath:/static/` (default). Dev compose points the backend at
 * an additional `file:/app/frontend-dist/` location fed by `vite build
 * --watch`, so a single `docker compose up` on port 8080 is enough — no
 * separate Vite dev server, no second port.
 *
 * Rules:
 * - Real files found in any configured location are served as-is.
 * - Requests under the `/api/` or `/actuator/` prefix are never rewritten —
 *   they hit the Spring `@RestController`s (higher precedence than
 *   resource handlers) or return 404 if unmapped.
 * - Requests with a file extension (e.g. `/foo.png`) that don't exist
 *   return 404 instead of the SPA shell, so missing assets fail loudly.
 * - Everything else (e.g. `/dashboard`, `/apps/my-app`) is served
 *   `index.html` so React Router can take over.
 */
@Configuration
class SpaWebConfig(
    private val webProperties: WebProperties,
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val locations = webProperties.resources.staticLocations
        registry.addResourceHandler("/**")
            .addResourceLocations(*locations)
            .resourceChain(true)
            .addResolver(SpaResourceResolver(locations))
    }
}

private class SpaResourceResolver(
    locations: Array<String>,
) : PathResourceResolver() {

    private val resourceLoader: ResourceLoader = DefaultResourceLoader()
    private val indexCandidates: List<String> =
        locations.map { "${it.trimEnd('/')}/index.html" }

    override fun getResource(resourcePath: String, location: Resource): Resource? {
        // Prefer the real file if it exists in the current location.
        super.getResource(resourcePath, location)?.let { return it }

        // Never rewrite API routes — let Spring return 404 for unmapped ones.
        if (API_PREFIXES.any { resourcePath.startsWith(it) }) return null

        // Anything with an extension that wasn't found is a missing asset — 404.
        if (resourcePath.contains('.')) return null

        // Deep link into the SPA — serve the first index.html we find,
        // searching configured locations in order.
        return indexCandidates
            .asSequence()
            .map { resourceLoader.getResource(it) }
            .firstOrNull { it.exists() }
    }

    private companion object {
        val API_PREFIXES = listOf("api/", "actuator/")
    }
}
