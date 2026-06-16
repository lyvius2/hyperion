package banghak.data.platform.hyperion.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class PageViewController {

    @GetMapping("/")
    fun main(): String = "main"
}