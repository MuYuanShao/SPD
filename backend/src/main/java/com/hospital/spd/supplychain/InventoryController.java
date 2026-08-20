package com.hospital.spd.supplychain;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.supplychain.service.InventoryService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Exposes inventory balance, batch, event, stocktaking, and batch price adjustment endpoints.
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/balances")
    public ApiResponse<Map<String, Object>> balances(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.balances(params));
    }

    @GetMapping("/events")
    public ApiResponse<Map<String, Object>> events(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.events(params));
    }

    @GetMapping("/batches")
    public ApiResponse<Map<String, Object>> batches(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.batches(params));
    }

    @PostMapping("/stocktaking")
    public ApiResponse<Map<String, Object>> createStocktaking(@RequestBody StocktakingRequest request) {
        return ApiResponse.ok(service.createStocktaking(request));
    }

    @PutMapping("/stocktaking/{stocktakingNo}/approve")
    public ApiResponse<Map<String, Object>> approveStocktaking(@PathVariable String stocktakingNo) {
        return ApiResponse.ok(service.approveStocktaking(stocktakingNo));
    }

    @PostMapping("/batch-price-adjustments")
    public ApiResponse<Map<String, Object>> createPriceAdjustment(@RequestBody BatchPriceAdjustmentRequest request) {
        return ApiResponse.ok(service.createPriceAdjustment(request));
    }

    @PutMapping("/batch-price-adjustments/{adjustmentNo}/approve")
    public ApiResponse<Map<String, Object>> approvePriceAdjustment(@PathVariable String adjustmentNo) {
        return ApiResponse.ok(service.approvePriceAdjustment(adjustmentNo));
    }

    @GetMapping("/stocktaking")
    public ApiResponse<Map<String, Object>> stocktakingList(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.stocktakingList(params));
    }

    @GetMapping("/batch-price-adjustments")
    public ApiResponse<Map<String, Object>> priceAdjustmentList(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.priceAdjustmentList(params));
    }

}
