package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record PurchaseOrderItemRequest(
        String productCode,
        @com.hospital.spd.common.WholeQuantity BigDecimal quantity,
        String unit,
        BigDecimal estimatedUnitPrice
) {
}
