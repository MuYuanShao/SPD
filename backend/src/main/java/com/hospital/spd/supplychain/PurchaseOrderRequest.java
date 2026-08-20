package com.hospital.spd.supplychain;

import java.util.List;

public record PurchaseOrderRequest(
        String supplierName,
        String orderSource,
        String expectedArrivalDate,
        List<PurchaseOrderItemRequest> items
) {
}
