package cloud.fiely.common.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "Lightweight liveness/identity endpoints.")
class HealthController {

    @GetMapping("/ping")
    @Operation(
        summary = "Ping the API",
        description = "Returns a static payload identifying the service. Useful as a cheap " +
            "liveness probe that doesn't touch the database.",
    )
    @SecurityRequirements // public endpoint — override the global bearerAuth requirement
    fun ping() = mapOf(
        "status" to "ok",
        "app" to "fiely"
    )
}
