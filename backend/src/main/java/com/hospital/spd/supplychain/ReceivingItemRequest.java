package com.hospital.spd.supplychain;

import java.math.BigDecimal;

public record ReceivingItemRequest(
        String productCode,
        String productionBatchNo,
        String udiCode,
        String productionDate,
        String expireDate,
        BigDecimal quantity,
        BigDecimal qualifiedQuantity,
        BigDecimal unqualifiedQuantity,
        Long purchaseOrderItemId
) {
    public ReceivingItemRequest(String productCode,String productionBatchNo,String udiCode,String productionDate,String expireDate,
            BigDecimal quantity,BigDecimal qualifiedQuantity,BigDecimal unqualifiedQuantity) {
        this(productCode,productionBatchNo,udiCode,productionDate,expireDate,quantity,qualifiedQuantity,unqualifiedQuantity,null);
    }
}
