package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record QuotaSafetyRequest(
        String deptCode,
        String deptName,
        Long templateId,
        String templateCode,
        String productCode,
        @com.hospital.spd.common.WholeQuantity BigDecimal minQty,
        @com.hospital.spd.common.WholeQuantity BigDecimal maxQty
) {
    public QuotaSafetyRequest(String deptName, String templateCode, String productCode,
                              @com.hospital.spd.common.WholeQuantity BigDecimal minQty, @com.hospital.spd.common.WholeQuantity BigDecimal maxQty) {
        this(null, deptName, null, templateCode, productCode, minQty, maxQty);
    }
}
