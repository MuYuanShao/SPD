package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record QuotaSafetyRequest(
        String deptName,
        String templateCode,
        String productCode,
        BigDecimal minQty,
        BigDecimal maxQty
) {
}
