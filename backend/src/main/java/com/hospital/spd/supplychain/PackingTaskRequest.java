package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record PackingTaskRequest(
        String templateCode,
        String warehouseName,
        BigDecimal packageCount,
        String remark
) {
}
