package com.hospital.spd.supplychain;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.supplychain.service.StockReturnService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Binds stock return queries, submissions and warehouse confirmations. */
@RestController
@RequestMapping("/stock-returns")
public class StockReturnController {
    private final StockReturnService returns;

    public StockReturnController(StockReturnService returns) {
        this.returns = returns;
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(returns.options());
    }

    @GetMapping("/candidates")
    public ApiResponse<List<Map<String, Object>>> candidates(@RequestParam long warehouseId, @RequestParam String type,
            @RequestParam(required = false) Long supplierId, @RequestParam(defaultValue = "") String batchNo,
            @RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(returns.candidates(warehouseId, type, supplierId, batchNo, keyword));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> records() {
        return ApiResponse.ok(returns.records(false));
    }

    @GetMapping("/picking-queue")
    public ApiResponse<List<Map<String, Object>>> queue() {
        return ApiResponse.ok(returns.records(true));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> submit(@RequestBody StockReturnService.ReturnRequest request) {
        return ApiResponse.ok(returns.submit(request));
    }

    @PutMapping("/{no}/warehouse-confirm")
    public ApiResponse<Map<String, Object>> confirmWarehouseReturn(@PathVariable String no) {
        return ApiResponse.ok(returns.confirmWarehouseReturn(no));
    }

    @PutMapping("/{no}/cancel")
    public ApiResponse<Map<String, Object>> cancelWarehouseReturn(@PathVariable String no) {
        return ApiResponse.ok(returns.cancelWarehouseReturn(no));
    }
}
