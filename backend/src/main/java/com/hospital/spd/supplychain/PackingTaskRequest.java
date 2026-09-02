package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record PackingTaskRequest(
        String templateCode,
        String warehouseName,
        BigDecimal packageCount,
        String remark,
        boolean allowPartial,
        BigDecimal expectedPackableCount
) {
    public PackingTaskRequest(String templateCode, String warehouseName, BigDecimal packageCount, String remark) {
        this(templateCode, warehouseName, packageCount, remark, false, null);
    }
}
