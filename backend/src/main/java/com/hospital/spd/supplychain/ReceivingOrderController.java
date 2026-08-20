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

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(service.options());
    }

    @GetMapping("/purchase-order/{orderNo}/items")
    public ApiResponse<Map<String, Object>> purchaseOrderItems(@PathVariable String orderNo) {
        return ApiResponse.ok(service.purchaseOrderItems(orderNo));
    }

}
