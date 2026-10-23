package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record StocktakingRequest(
        String warehouseName,
        String systemBatchNo,
        @com.hospital.spd.common.WholeQuantity BigDecimal actualQty,
        String reason
) {
}
