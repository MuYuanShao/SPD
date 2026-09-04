package com.hospital.spd.supplychain;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.supplychain.service.ReceivingOrderService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Exposes receiving-order endpoints and delegates acceptance business rules to the service layer.
 */
@RestController
@RequestMapping("/receiving-orders")
public class ReceivingOrderController {

    private final ReceivingOrderService service;

    public ReceivingOrderController(ReceivingOrderService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.list(params));
    }

    @GetMapping("/{receivingNo}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String receivingNo,
                                                   @RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.detail(receivingNo, params));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody ReceivingOrderRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{receivingNo}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String receivingNo,
                                                   @RequestBody ReceivingOrderRequest request) {
        return ApiResponse.ok(service.update(receivingNo, request));
    }

    @PutMapping("/{receivingNo}/action")
    public ApiResponse<Map<String, Object>> action(@PathVariable String receivingNo,
                                                   @RequestBody ReceivingActionRequest request) {
        return ApiResponse.ok(service.action(receivingNo, request));
    }

    @PutMapping("/{receivingNo}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable String receivingNo,
                                                    @RequestBody(required = false) ReceivingActionRequest request) {
        String opinion = request == null ? null : request.opinion();
        return ApiResponse.ok(service.action(receivingNo, new ReceivingActionRequest("approve", opinion)));
    }

    @PutMapping("/{receivingNo}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable String receivingNo,
                                                   @RequestBody ReceivingActionRequest request) {
        return ApiResponse.ok(service.action(receivingNo, new ReceivingActionRequest("reject", request.opinion())));
    }

    @GetMapping("/{receivingNo}/items")
    public ApiResponse<Map<String, Object>> items(@PathVariable String receivingNo,
                                                  @RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.items(receivingNo, params));
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(service.options());
    }

    @GetMapping("/options/purchase-orders")
    public ApiResponse<Map<String, Object>> purchaseOrderOptions(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.purchaseOrderOptions(params));
    }

    @GetMapping("/options/warehouses")
    public ApiResponse<Map<String, Object>> warehouseOptions(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.warehouseOptions(params));
    }

    @GetMapping("/options/suppliers")
    public ApiResponse<Map<String, Object>> supplierOptions(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.supplierOptions(params));
    }

    @GetMapping("/options/products")
    public ApiResponse<Map<String, Object>> productOptions(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.productOptions(params));
    }

    @GetMapping("/purchase-order/{orderNo}/items")
    public ApiResponse<Map<String, Object>> purchaseOrderItems(@PathVariable String orderNo) {
        return ApiResponse.ok(service.purchaseOrderItems(orderNo));
    }

}
