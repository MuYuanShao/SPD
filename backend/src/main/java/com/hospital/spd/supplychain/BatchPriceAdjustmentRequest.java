package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record BatchPriceAdjustmentRequest(
        String systemBatchNo,
        BigDecimal newUnitPrice,
        String reason
) {
}
