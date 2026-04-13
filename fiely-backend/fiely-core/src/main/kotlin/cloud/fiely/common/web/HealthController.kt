package cloud.fiely.common.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/ping")
    fun ping() = mapOf(
        "status" to "ok",
        "app" to "fiely"
    )
}
