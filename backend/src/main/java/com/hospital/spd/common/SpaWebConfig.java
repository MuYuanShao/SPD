package com.hospital.spd.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves a bundled frontend build from the local file system for offline one-click deployments.
 * Active only when the spd.web.static-dir property is configured.
 */
@Configuration
@ConditionalOnProperty(name = "spd.web.static-dir")
public class SpaWebConfig implements WebMvcConfigurer {

    private final String staticDir;

    public SpaWebConfig(@Value("${spd.web.static-dir}") String staticDir) {
        this.staticDir = staticDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = staticDir.endsWith("/") || staticDir.endsWith("\\") ? staticDir : staticDir + "/";
        registry.addResourceHandler("/app/**")
                .addResourceLocations("file:" + location)
                .setCachePeriod(3600);
    }
}
