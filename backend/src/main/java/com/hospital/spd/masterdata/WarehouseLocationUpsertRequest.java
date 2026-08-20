package com.hospital.spd.masterdata;

import java.math.BigDecimal;

public record WarehouseLocationUpsertRequest(
        String locationCode,
        String locationType,
        BigDecimal capacityLimit,
        String productCode,
        Integer status
) {
}
