package com.hospital.spd.supplychain;

import java.util.List;

public record PurchaseOrderRequest(
        String supplierName,
        String orderSource,
        String purchaseType,
        String expectedArrivalDate,
        @jakarta.validation.Valid List<PurchaseOrderItemRequest> items
) {
    public PurchaseOrderRequest(String supplierName,
                                String orderSource,
                                String expectedArrivalDate,
                                @jakarta.validation.Valid List<PurchaseOrderItemRequest> items) {
        this(supplierName, orderSource, null, expectedArrivalDate, items);
    }
}
