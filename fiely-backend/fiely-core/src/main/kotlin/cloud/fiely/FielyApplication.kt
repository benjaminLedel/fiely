package cloud.fiely

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FielyApplication

fun main(args: Array<String>) {
    runApplication<FielyApplication>(*args)
}
