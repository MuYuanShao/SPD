package com.hospital.spd.supplychain;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.supplychain.service.PurchaseOrderAttachmentService;
import com.hospital.spd.supplychain.service.PurchaseOrderService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Exposes purchase demand, plan, order, and tracking endpoints.
 */
@RestController
@RequestMapping("/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;
    private final PurchaseOrderAttachmentService attachmentService;

    public PurchaseOrderController(PurchaseOrderService service,
                                   PurchaseOrderAttachmentService attachmentService) {
        this.service = service;
        this.attachmentService = attachmentService;
    }

    // ==================== 采购订单 ====================

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.listOrders(params));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String orderNo) {
        return ApiResponse.ok(service.getDetail(orderNo));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody PurchaseOrderRequest request) {
        return ApiResponse.ok(service.createOrder(request));
    }

    @PutMapping("/{orderNo}/action")
    public ApiResponse<Map<String, Object>> action(@PathVariable String orderNo,
                                                   @RequestBody PurchaseOrderActionRequest request) {
        return ApiResponse.ok(service.performAction(orderNo, request));
    }

    @PostMapping("/{orderNo}/remarks")
    public ApiResponse<Map<String, Object>> addRemark(@PathVariable String orderNo,
                                                      @RequestBody Map<String, String> request) {
        return ApiResponse.ok(service.addRemark(orderNo, request.get("remark")));
    }

    @GetMapping("/{orderNo}/attachments")
    public ApiResponse<List<Map<String, Object>>> attachments(@PathVariable String orderNo) {
        return ApiResponse.ok(attachmentService.list(orderNo));
    }

    @PostMapping("/{orderNo}/attachments")
    public ApiResponse<Map<String, Object>> uploadAttachment(@PathVariable String orderNo,
                                                             @RequestParam("file") MultipartFile file,
                                                             @RequestParam(defaultValue = "other") String category) {
        return ApiResponse.ok(attachmentService.upload(orderNo, file, category));
    }

    @GetMapping("/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> attachmentFile(@PathVariable Long attachmentId) {
        return attachmentService.file(attachmentId);
    }

    // ==================== 采购需求 ====================

    @GetMapping("/demands")
    public ApiResponse<Map<String, Object>> demands(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.listDemands(params));
    }

    @PostMapping("/demands")
    public ApiResponse<Map<String, Object>> createDemand(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.createDemand(request));
    }

    @PutMapping("/demands/{demandNo}/action")
    public ApiResponse<Map<String, Object>> demandAction(@PathVariable String demandNo,
                                                         @RequestBody PurchaseOrderActionRequest request) {
        return ApiResponse.ok(service.performDemandAction(demandNo, request));
    }

    // ==================== 采购计划 ====================

    @GetMapping("/plans")
    public ApiResponse<Map<String, Object>> plans(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.listPlans(params));
    }

    @PostMapping("/plans/from-demands")
    public ApiResponse<Map<String, Object>> createPlanFromDemands(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.createPlanFromDemands(request));
    }

    @PutMapping("/plans/{planNo}/action")
    public ApiResponse<Map<String, Object>> planAction(@PathVariable String planNo,
                                                       @RequestBody PurchaseOrderActionRequest request) {
        return ApiResponse.ok(service.performPlanAction(planNo, request));
    }

    // ==================== 跟踪与选项 ====================

    @GetMapping("/{orderNo}/tracking")
    public ApiResponse<List<Map<String, Object>>> tracking(@PathVariable String orderNo) {
        return ApiResponse.ok(service.getTracking(orderNo));
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(service.getOptions());
    }

    @GetMapping("/smart-replenishment-analysis")
    public ApiResponse<Map<String, Object>> smartReplenishmentAnalysis(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.smartReplenishmentAnalysis(params));
    }
}
