package com.hospital.spd.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class WriteFieldRequestAdviceTest {
    @Configuration
    @EnableWebMvc
    static class MvcConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @RestController
    static class Endpoint {
        @PostMapping("/fields") ApiResponse<Void> write(@RequestBody Map<String, Object> body) { return ApiResponse.ok(null); }
        @GetMapping("/fields") ApiResponse<Void> read() { return ApiResponse.ok(null); }
    }

    @Test
    void rejectsNestedJsonStringsAndWriteParametersWhileAcceptingJsonFormattingAndReadFilters() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(MvcConfiguration.class, WebMvcConfig.class, WriteFieldRequestAdvice.class,
                    GlobalExceptionHandler.class, Endpoint.class);
            context.refresh();
            var mvc = webAppContextSetup(context).build();
            for (String value : new String[]{"a b", "a\\nb", "a\\tb", "a\\u3000b"}) {
                mvc.perform(post("/fields").contentType("application/json")
                                .content("{\"items\":[{\"name\":\"" + value + "\"}]}"))
                        .andExpect(jsonPath("$.code").value(400))
                        .andExpect(jsonPath("$.message").value("不支持空格及换行符存在！"));
            }
            mvc.perform(post("/fields").param("name", "a b").contentType("application/json").content("{}"))
                    .andExpect(jsonPath("$.message").value("不支持空格及换行符存在！"));
            mvc.perform(post("/fields").contentType("application/json").content("{\n \"name\": \"ab\"\n}"))
                    .andExpect(jsonPath("$.code").value(0));
            mvc.perform(get("/fields").param("name", "a b")).andExpect(jsonPath("$.code").value(0));
        }
    }
}
