package com.hospital.spd.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcConfigTest {

    @Test
    void keepsActuatorJsonMediaTypeWhenCustomizingJacksonConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        List<HttpMessageConverter<?>> converters = new ArrayList<>(List.of(converter));

        new WebMvcConfig().extendMessageConverters(converters);

        MediaType actuatorJson = MediaType.parseMediaType("application/vnd.spring-boot.actuator.v3+json");
        assertThat(converter.getSupportedMediaTypes()).contains(MediaType.APPLICATION_JSON);
        assertThat(converter.getSupportedMediaTypes()).contains(actuatorJson);
        assertThat(converter.canWrite(Map.class, actuatorJson)).isTrue();
    }
}
