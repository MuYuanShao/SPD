package com.hospital.spd.supplychain;

import com.hospital.spd.common.PageRequest;

import java.util.Map;

/** Typed query contract for immutable inventory ledger reads. */
public record InventoryEventQuery(
        String eventNo, String deptName, String warehouseName, String productCode, String productName,
        String batchNo, String productionBatchNo, String manufacturerName, String supplierName,
        String sourceBizNo, String sourceBizType, String transactionTypeCode,
        String startTime, String endTime, PageRequest pageRequest
) {
    public static InventoryEventQuery from(Map<String, String> params) {
        String stableType = text(params.get("transactionTypeCode"));
        if (stableType == null) stableType = text(params.get("transactionType"));
        return new InventoryEventQuery(text(params.get("eventNo")), text(params.get("deptName")),
                text(params.get("warehouseName")), text(params.get("productCode")), text(params.get("productName")),
                text(params.get("batchNo")), text(params.get("productionBatchNo")),
                text(params.get("manufacturerName")), text(params.get("supplierName")),
                text(params.get("sourceBizNo")), text(params.get("sourceBizType")), stableType,
                text(params.get("startTime")), text(params.get("endTime")), PageRequest.from(params));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
