package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Provides a lightweight health endpoint for local and deployment checks.
 */
@RestController
public class HealthController {

    private final int port;

    public HealthController(@Value("${server.port:1818}") int port) {
        this.port = port;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of(
                "service", "hospital-spd",
                "status", "UP",
                "port", String.valueOf(port)
        ));
    }
}
