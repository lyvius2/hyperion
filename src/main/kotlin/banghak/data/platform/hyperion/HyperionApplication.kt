package banghak.data.platform.hyperion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class HyperionApplication

fun main(args: Array<String>) {
    runApplication<HyperionApplication>(*args)
}
