package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.system.service.OperationCockpitSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the month-scoped operational cockpit read model.
 */
@RestController
@RequestMapping("/operation-cockpit")
public class OperationCockpitController {

    private final OperationCockpitSnapshotService operationCockpitService;

    public OperationCockpitController(OperationCockpitSnapshotService operationCockpitService) {
        this.operationCockpitService = operationCockpitService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> cockpit(@RequestParam(required = false) String month) {
        return ApiResponse.ok(operationCockpitService.cockpit(month));
    }
}
