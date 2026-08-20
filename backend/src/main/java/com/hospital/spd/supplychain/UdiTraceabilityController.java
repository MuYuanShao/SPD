package com.hospital.spd.supplychain;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.supplychain.service.UdiTraceabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes UDI and unique-code traceability query endpoints.
 */
@RestController
@RequestMapping("/udi-traceability")
public class UdiTraceabilityController {

    private final UdiTraceabilityService service;

    public UdiTraceabilityController(UdiTraceabilityService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.summary(params));
    }

    @GetMapping("/records")
    public ApiResponse<Map<String, Object>> records(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.records(params));
    }

    @GetMapping("/records/{code}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String code) {
        return ApiResponse.ok(service.detail(code));
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(service.options());
    }
}
