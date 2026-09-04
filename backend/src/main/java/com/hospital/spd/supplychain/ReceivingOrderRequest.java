package com.hospital.spd.supplychain;

import java.util.List;

public record ReceivingOrderRequest(
        String sourceType,
        String purchaseOrderNo,
        Long supplierId,
        String supplierName,
        String warehouseCode,
        String warehouseName,
        String receivingType,
        Boolean isAgent,
        String remark,
        List<ReceivingItemRequest> items
) {
    public ReceivingOrderRequest(String purchaseOrderNo, String supplierName, String warehouseName,
                                 String receivingType, Boolean isAgent, String remark,
                                 List<ReceivingItemRequest> items) {
        this(null, purchaseOrderNo, null, supplierName, null, warehouseName,
                receivingType, isAgent, remark, items);
    }
}
