package com.hospital.spd.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final MediaType APPLICATION_JSON_SUFFIX = MediaType.parseMediaType("application/*+json");
    private static final MediaType ACTUATOR_V3_JSON = MediaType.parseMediaType("application/vnd.spring-boot.actuator.v3+json");

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
