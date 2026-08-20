package com.hospital.spd.supplychain;

import java.util.List;

public record ReceivingOrderRequest(
        String purchaseOrderNo,
        String supplierName,
        String warehouseName,
        String remark,
        List<ReceivingItemRequest> items
) {
}
