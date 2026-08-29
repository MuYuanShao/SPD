package com.hospital.spd.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards SPA history-mode routes to the bundled index.html in offline one-click deployments.
 * Only registered when spd.web.static-dir is configured; API routes live under the /api context
 * path and never reach these mappings.
 */
@Controller
@ConditionalOnProperty(name = "spd.web.static-dir")
public class SpaFallbackController {

    @GetMapping("/")
    public String redirectToApplication() {
        return "redirect:/app/";
    }

    @GetMapping(value = {"/app", "/app/", "/app/{*path}"})
    public String forwardToIndex() {
        return "forward:/app/index.html";
    }
}
