package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record PurchaseOrderItemRequest(
        String productCode,
        BigDecimal quantity,
        String unit,
        BigDecimal estimatedUnitPrice
) {
}
