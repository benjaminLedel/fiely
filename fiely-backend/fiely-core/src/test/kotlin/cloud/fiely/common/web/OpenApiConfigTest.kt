package cloud.fiely.common.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Smoke test asserting that springdoc-openapi is wired up and picks up
 * the metadata from [OpenApiConfig]. If someone removes the dependency
 * or the config bean, these assertions fail loudly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `openapi spec is served with fiely metadata`() {
        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.openapi") { exists() }
                jsonPath("$.info.title") { value("Fiely API") }
                jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
                // Both core controllers should be picked up in the scan.
                jsonPath("$.paths./api/ping") { exists() }
                jsonPath("$.paths./api/auth/login") { exists() }
            }
    }

    @Test
    fun `swagger ui page is reachable`() {
        // Springdoc serves the UI shell behind /swagger-ui/index.html and
        // redirects /swagger-ui.html to it. A 200 or 3xx both mean the
        // handler is wired up.
        mockMvc.get("/swagger-ui/index.html")
            .andExpect {
                status { is2xxSuccessful() }
            }
    }
}
