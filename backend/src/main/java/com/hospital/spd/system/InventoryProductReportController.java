package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.system.service.DashboardWorkbenchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Exposes scoped inventory product details in the report center. */
@RestController
@RequestMapping("/report-center/inventory-products")
public class InventoryProductReportController {
    private final DashboardWorkbenchService service;

    public InventoryProductReportController(DashboardWorkbenchService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.products(params));
    }
}
