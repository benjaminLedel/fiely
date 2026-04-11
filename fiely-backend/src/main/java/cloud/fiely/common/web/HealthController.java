package cloud.fiely.common.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight smoke-test endpoint. Complements Spring Boot Actuator's
 * {@code /actuator/health} with a predictable payload for simple curl checks.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of(
                "status", "ok",
                "app", "fiely");
    }
}
