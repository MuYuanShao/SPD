package com.hospital.spd.masterdata;

import java.math.BigDecimal;
import java.util.List;

public record ProductBatchUpdateRequest(
        List<String> productCodes,
        String volumeBased,
        String domestic,
        @jakarta.validation.constraints.DecimalMin(value = "0", message = "采购价不能为负数") BigDecimal purchasePrice,
        String unit,
        String registrationNo,
        String contractCode,
        String firstCategory,
        String secondCategory,
        String thirdCategory,
        String chargeable
) {
}
