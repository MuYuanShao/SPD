package com.hospital.spd.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redirects the offline deployment entry into the bundled application namespace.
 * Resource handling and SPA history fallback belong to SpaWebConfig.
 */
@Controller
@ConditionalOnProperty(name = "spd.web.static-dir")
public class SpaFallbackController {

    @GetMapping("/")
    public String redirectToApplication() {
        return "redirect:/app/";
    }

    @GetMapping("/app")
    public String redirectToApplicationDirectory() {
        return "redirect:/app/";
    }

    @GetMapping("/app/")
    public String applicationIndex() {
        return "forward:/app/index.html";
    }
}
