package com.hospital.spd.masterdata;

import java.math.BigDecimal;

public record PendingProductApplicationRequest(
        String applicationType,
        String productCode,
        String productName,
        String specModel,
        String brand,
        String manufacturerName,
        String supplierName,
        String unit,
        BigDecimal purchasePrice,
        BigDecimal retailPrice,
        BigDecimal minPurchaseQty,
        String purchaseUnit,
        BigDecimal conversionRate,
        BigDecimal purchasePackageQty,
        String udiCode,
        String registrationNo,
        String registrationExpireDate,
        String productionLicenseNo,
        String businessLicenseNo,
        Boolean volumeBased,
        Boolean centralizedProcurement,
        Boolean domestic,
        String contractCode,
        String firstCategory,
        String secondCategory,
        String thirdCategory,
        Boolean chargeable,
        String tenderSubCode,
        Integer qualificationAttachmentCount,
        Boolean highValue,
        Boolean coldChain,
        Boolean quotaManaged,
        String storageCondition,
        String changeReason
) {
}
