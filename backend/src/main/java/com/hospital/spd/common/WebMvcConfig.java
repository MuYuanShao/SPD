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
                                file -> requireValidUploadFileName(file.getOriginalFilename())));
                    }
                }
                return true;
            }
        });
    }

    /**
     * Uploaded file names are opaque user data, not structured business fields.
     * Real names legitimately contain spaces (e.g. "营业执照 (1).pdf"), so the no-whitespace
     * rule used for request parameters and JSON body values does not apply here.
     * Only control characters are rejected, because they break HTTP headers and log files.
     * Path separators are also rejected to keep the path-traversal guard meaningful.
     */
    private static void requireValidUploadFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isEmpty()) return;
        for (int index = 0; index < originalFileName.length(); index++) {
            char character = originalFileName.charAt(index);
            if (character < 0x20 || character == 0x7F) {
                throw new IllegalArgumentException("附件文件名不允许包含控制字符");
            }
        }
        if (originalFileName.indexOf('/') >= 0 || originalFileName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("附件文件名不允许包含路径分隔符");
        }
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
