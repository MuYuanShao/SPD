package com.hospital.spd.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;
import java.io.IOException;
import java.nio.file.Path;

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
        String location = Path.of(staticDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/app/**")
                .addResourceLocations(location)
                .setCachePeriod(0)
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = resourcePath.isEmpty() ? null : super.getResource(resourcePath, location);
                        if (resource != null) return resource;
                        // Missing assets must stay 404; only extensionless application routes use the SPA shell.
                        if (resourcePath.startsWith("assets/") || resourcePath.contains(".")) return null;
                        return super.getResource("index.html", location);
                    }
                });
    }
}
