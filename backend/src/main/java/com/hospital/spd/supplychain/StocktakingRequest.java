package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record StocktakingRequest(
        String warehouseName,
        String systemBatchNo,
        BigDecimal actualQty,
        String reason
) {
}
