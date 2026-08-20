package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record QuotaTemplateRequest(
        String templateCode,
        String templateName,
        String deptName,
        String productCode,
        BigDecimal quantity,
        String unit
) {
}
