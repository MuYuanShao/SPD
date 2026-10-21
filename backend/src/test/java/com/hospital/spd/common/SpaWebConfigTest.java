package com.hospital.spd.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpaWebConfigTest {
    @TempDir Path directory;

    @Configuration
    @EnableWebMvc
    static class MvcConfiguration { }

    @Test
    void historyRoutesServeIndexWhileAssetsKeepTheirContentsAndMissingAssetsStay404() throws Exception {
        Files.writeString(directory.resolve("index.html"), "<html>SPD shell</html>");
        Files.createDirectories(directory.resolve("assets"));
        Files.writeString(directory.resolve("assets/app.js"), "window.SPD=true;");
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "test", java.util.Map.of("spd.web.static-dir", directory.toString())));
            context.register(MvcConfiguration.class, SpaWebConfig.class, SpaFallbackController.class);
            context.refresh();
            var mvc = webAppContextSetup(context).build();
            mvc.perform(get("/app/")).andExpect(status().isOk()).andExpect(forwardedUrl("/app/index.html"));
            for (String path : java.util.List.of("/app/index.html", "/app/features/audit-log")) {
                mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string("<html>SPD shell</html>"));
            }
            mvc.perform(get("/app/assets/app.js")).andExpect(status().isOk()).andExpect(content().string("window.SPD=true;"));
            mvc.perform(get("/app/assets/missing.js")).andExpect(status().isNotFound());
            mvc.perform(get("/inventory")).andExpect(status().isNotFound());
        }
    }
}
