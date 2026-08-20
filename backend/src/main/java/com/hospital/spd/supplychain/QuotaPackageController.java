package com.hospital.spd.supplychain;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.supplychain.service.PackingTaskService;
import com.hospital.spd.supplychain.service.QuotaTemplateService;
import com.hospital.spd.supplychain.service.SafetyStockService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exposes quota template, safety stock, packing task, label, unpacking, and package event endpoints.
 */
@RestController
@RequestMapping("/quota-packages")
public class QuotaPackageController {
    private final QuotaTemplateService templateService;
    private final SafetyStockService safetyService;
    private final PackingTaskService packingTaskService;

    public QuotaPackageController(QuotaTemplateService templateService,
                                  SafetyStockService safetyService,
                                  PackingTaskService packingTaskService) {
        this.templateService = templateService;
        this.safetyService = safetyService;
        this.packingTaskService = packingTaskService;
    }

    @GetMapping("/templates")
    public ApiResponse<Map<String, Object>> templates(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(templateService.templates(params));
    }

    @PostMapping("/templates")
    public ApiResponse<Map<String, Object>> createTemplate(@RequestBody QuotaTemplateRequest request) {
        return ApiResponse.ok(templateService.createTemplate(request));
    }

    @PutMapping("/templates/{templateCode}/disable")
    public ApiResponse<Map<String, Object>> disableTemplate(@PathVariable String templateCode) {
        return ApiResponse.ok(templateService.disableTemplate(templateCode));
    }

    @PutMapping("/templates/{templateCode}/enable")
    public ApiResponse<Map<String, Object>> enableTemplate(@PathVariable String templateCode) {
        return ApiResponse.ok(templateService.enableTemplate(templateCode));
    }

    @GetMapping("/safety")
    public ApiResponse<Map<String, Object>> safety(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(safetyService.safety(params));
    }

    @PostMapping("/safety")
    public ApiResponse<Map<String, Object>> saveSafety(@RequestBody QuotaSafetyRequest request) {
        return ApiResponse.ok(safetyService.saveSafety(request));
    }

    @GetMapping("/packing-options")
    public ApiResponse<Map<String, Object>> packingOptions() {
        return ApiResponse.ok(packingTaskService.packingOptions());
    }

    @GetMapping("/requisition-catalog")
    public ApiResponse<Map<String, Object>> requisitionCatalog(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(templateService.requisitionCatalog(params));
    }

    @GetMapping("/packing-tasks")
    public ApiResponse<Map<String, Object>> tasks(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(packingTaskService.tasks(params));
    }

    @PostMapping("/packing-tasks")
    public ApiResponse<Map<String, Object>> createTask(@RequestBody PackingTaskRequest request) {
        return ApiResponse.ok(packingTaskService.createTask(request));
    }

    @PutMapping("/packing-tasks/{taskNo}/confirm")
    public ApiResponse<Map<String, Object>> confirmTask(@PathVariable String taskNo) {
        return ApiResponse.ok(packingTaskService.confirmTask(taskNo));
    }

    @PutMapping("/packing-tasks/{taskNo}/cancel")
    public ApiResponse<Map<String, Object>> cancelTask(@PathVariable String taskNo,
                                                       @RequestBody(required = false) PackageActionRequest request) {
        return ApiResponse.ok(packingTaskService.cancelTask(taskNo, request));
    }

    @PutMapping("/packing-tasks/{taskNo}/terminate")
    public ApiResponse<Map<String, Object>> terminateTask(@PathVariable String taskNo,
                                                          @RequestBody(required = false) PackageActionRequest request) {
        return ApiResponse.ok(packingTaskService.terminateTask(taskNo, request));
    }

    @PutMapping("/packing-tasks/{taskNo}/recalculate")
    public ApiResponse<Map<String, Object>> recalculateTask(@PathVariable String taskNo) {
        return ApiResponse.ok(packingTaskService.recalculateTask(taskNo));
    }

    @GetMapping("/packing-tasks/{taskNo}/reservations")
    public ApiResponse<List<Map<String, Object>>> taskReservations(@PathVariable String taskNo) {
        return ApiResponse.ok(packingTaskService.taskReservations(taskNo));
    }

    @GetMapping("/labels")
    public ApiResponse<Map<String, Object>> labels(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(packingTaskService.labels(params));
    }

    @PutMapping("/labels/{labelNo}/unpack")
    public ApiResponse<Map<String, Object>> unpack(@PathVariable String labelNo,
                                                   @RequestBody(required = false) PackageActionRequest request) {
        return ApiResponse.ok(packingTaskService.unpack(labelNo, request));
    }

    @PutMapping("/labels/{labelNo}/print")
    public ApiResponse<Map<String, Object>> printLabel(@PathVariable String labelNo) {
        return ApiResponse.ok(packingTaskService.printLabel(labelNo));
    }

    @GetMapping("/events")
    public ApiResponse<Map<String, Object>> packageEvents(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(packingTaskService.packageEvents(params));
    }
}
