package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.system.service.SystemConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Exposes configuration hit explanation endpoints for UI troubleshooting.
 */
@RestController
@RequestMapping("/config-hit-explanation")
public class ConfigHitExplanationController {
    private final SystemConfigService systemConfigService;

    public ConfigHitExplanationController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public ApiResponse<ConfigHitExplanation> explain(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(systemConfigService.explain(params));
    }
}
