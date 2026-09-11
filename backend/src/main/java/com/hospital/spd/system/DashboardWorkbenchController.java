package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.system.service.DashboardWorkbenchService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Home workbench contracts; preserves the existing realtime dashboard endpoint. */
@RestController
@RequestMapping("/dashboard")
public class DashboardWorkbenchController {
    private final DashboardWorkbenchService service;
    public DashboardWorkbenchController(DashboardWorkbenchService service) { this.service = service; }
    @GetMapping("/workbench")
    public ApiResponse<Map<String, Object>> workbench() { return ApiResponse.ok(service.workbench()); }
    @GetMapping("/products")
    public ApiResponse<Map<String, Object>> products(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.products(params));
    }
}
