package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.system.service.SystemConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exposes CRUD and status endpoints for scoped system configuration rows.
 */
@RestController
@RequestMapping("/system-config")
public class SystemConfigController {
    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public ApiResponse<List<SystemConfigRow>> list(@RequestParam(required = false) String configType,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status) {
        return ApiResponse.ok(systemConfigService.list(configType, keyword, status));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody SystemConfigRequest request) {
        return ApiResponse.ok(systemConfigService.create(request));
    }

    @PutMapping("/{configId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long configId,
                                                   @RequestBody SystemConfigRequest request) {
        return ApiResponse.ok(systemConfigService.update(configId, request));
    }

    @PutMapping("/{configId}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long configId,
                                                         @RequestBody Map<String, Integer> request) {
        return ApiResponse.ok(systemConfigService.updateStatus(configId, request.getOrDefault("status", 1)));
    }
}
