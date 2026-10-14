package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.system.service.AuditLogQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Reads durable audit logs produced by {@code AuditLogService}.
 * <p>
 * Supports paged list, single-record detail, and operation-type facet for
 * the front-end audit-log page. Detail returns the full before/after JSON
 * snapshot so the UI can render a before/after diff panel.
 */
@RestController
@RequestMapping("/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService service;

    public AuditLogController(AuditLogQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.list(params));
    }

    @GetMapping("/operation-types")
    public ApiResponse<Map<String, Object>> operationTypes() {
        return ApiResponse.ok(service.operationTypes());
    }

    @GetMapping("/biz-types")
    public ApiResponse<Map<String, Object>> bizTypes() {
        return ApiResponse.ok(service.bizTypes());
    }

    @GetMapping("/{auditId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long auditId) {
        return ApiResponse.ok(service.detail(auditId));
    }
}
