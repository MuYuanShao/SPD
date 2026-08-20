package com.hospital.spd.specialty;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.specialty.service.QuotaPackageTraceCommandService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes scan-driven signing and consumption commands for low-value quota packages.
 */
@RestController
@RequestMapping("/udi-traceability/quota-packages")
public class QuotaPackageTraceController {

    private final QuotaPackageTraceCommandService commandService;

    public QuotaPackageTraceController(QuotaPackageTraceCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/sign")
    public ApiResponse<Map<String, Object>> sign(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(commandService.sign(body));
    }

    @PostMapping("/consume")
    public ApiResponse<Map<String, Object>> consume(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(commandService.consume(body));
    }
}
