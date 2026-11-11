package com.hospital.spd.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/** Checks JSON string values before Jackson binds or coerces them. */
@ControllerAdvice
public class WriteFieldRequestAdvice extends RequestBodyAdviceAdapter {
    private final ObjectMapper mapper;
    private final HttpServletRequest request;

    public WriteFieldRequestAdvice(ObjectMapper mapper, HttpServletRequest request) {
        this.mapper = mapper;
        this.request = request;
    }

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType)
                && WriteFieldWhitespace.applies(request.getMethod(), path);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type targetType,
                                          Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        byte[] body = input.getBody().readAllBytes();
        try (JsonParser parser = mapper.getFactory().createParser(body)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.VALUE_STRING) {
                    WriteFieldWhitespace.requireValid(parser.getText());
                }
            }
        } catch (IOException ex) {
            throw new HttpMessageNotReadableException("Invalid JSON body", ex, input);
        }
        return new HttpInputMessage() {
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
        };
    }
}
