package com.hospital.spd.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    @Test
    void bundledWebEntryAndAssetsArePublicButBusinessApisRemainProtected() {
        assertThat(SecurityConfig.isBundledWebRequest(request("GET", "/", "/api/"))).isTrue();
        assertThat(SecurityConfig.isBundledWebRequest(request("GET", "/app/assets/index.js",
                "/api/app/assets/index.js"))).isTrue();
        assertThat(SecurityConfig.isBundledWebRequest(request("POST", "/app/assets/index.js",
                "/api/app/assets/index.js"))).isFalse();
        assertThat(SecurityConfig.isBundledWebRequest(request("GET", "/inventory", "/api/inventory"))).isFalse();
    }

    private HttpServletRequest request(String method, String servletPath, String requestUri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getServletPath()).thenReturn(servletPath);
        when(request.getRequestURI()).thenReturn(requestUri);
        return request;
    }
}
