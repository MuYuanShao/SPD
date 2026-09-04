package com.hospital.spd.supplychain;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.supplychain.service.InventoryEventQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Exposes immutable inventory-ledger list, detail, and option resources. */
@RestController
@RequestMapping("/inventory/events")
public class InventoryEventController {

    private final InventoryEventQueryService service;

    public InventoryEventController(InventoryEventQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(service.list(InventoryEventQuery.from(params)));
    }

    @GetMapping("/{eventNo}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String eventNo) {
        return ApiResponse.ok(service.detail(eventNo));
    }

    @GetMapping("/options/transaction-types")
    public ApiResponse<Map<String, Object>> transactionTypes() {
        return ApiResponse.ok(service.transactionTypeOptions());
    }
}
