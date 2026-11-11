package com.hospital.spd.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final MediaType APPLICATION_JSON_SUFFIX = MediaType.parseMediaType("application/*+json");
    private static final MediaType ACTUATOR_V3_JSON = MediaType.parseMediaType("application/vnd.spring-boot.actuator.v3+json");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if (WriteFieldWhitespace.applies(request.getMethod(), path)) {
                    for (String[] values : request.getParameterMap().values()) {
                        for (String value : values) WriteFieldWhitespace.requireValid(value);
                    }
                    if (request instanceof MultipartHttpServletRequest multipart) {
                        multipart.getMultiFileMap().values().forEach(files -> files.forEach(
                                file -> WriteFieldWhitespace.requireValid(file.getOriginalFilename())));
                    }
                }
                return true;
            }
        });
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(converter -> {
                    converter.setDefaultCharset(StandardCharsets.UTF_8);
                    List<MediaType> mediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
                    if (!mediaTypes.contains(MediaType.APPLICATION_JSON)) {
                        mediaTypes.add(MediaType.APPLICATION_JSON);
                    }
                    if (!mediaTypes.contains(APPLICATION_JSON_SUFFIX)) {
                        mediaTypes.add(APPLICATION_JSON_SUFFIX);
                    }
                    if (!mediaTypes.contains(ACTUATOR_V3_JSON)) {
                        mediaTypes.add(ACTUATOR_V3_JSON);
                    }
                    converter.setSupportedMediaTypes(mediaTypes);
                });
    }
}
