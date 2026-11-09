package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record PackingTaskRequest(
        String templateCode,
        String warehouseName,
        @com.hospital.spd.common.WholeQuantity BigDecimal packageCount,
        String remark,
        boolean allowPartial,
        @com.hospital.spd.common.WholeQuantity BigDecimal expectedPackableCount,
        Long expectedTemplateId
) {
    public PackingTaskRequest(String templateCode, String warehouseName, BigDecimal packageCount, String remark,
                              boolean allowPartial, BigDecimal expectedPackableCount) {
        this(templateCode, warehouseName, packageCount, remark, allowPartial, expectedPackableCount, null);
    }

    public PackingTaskRequest(String templateCode, String warehouseName, @com.hospital.spd.common.WholeQuantity BigDecimal packageCount, String remark) {
        this(templateCode, warehouseName, packageCount, remark, false, null);
    }
}
