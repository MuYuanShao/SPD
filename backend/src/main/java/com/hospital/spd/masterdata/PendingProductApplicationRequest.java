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
        @jakarta.validation.constraints.DecimalMin(value = "0", message = "采购价不能为负数") BigDecimal purchasePrice,
        @jakarta.validation.constraints.DecimalMin(value = "0", message = "零售价不能为负数") BigDecimal retailPrice,
        @com.hospital.spd.common.WholeQuantity BigDecimal minPurchaseQty,
        String purchaseUnit,
        @com.hospital.spd.common.WholeQuantity BigDecimal conversionRate,
        @com.hospital.spd.common.WholeQuantity BigDecimal purchasePackageQty,
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
        Boolean keyMonitored,
        String storageCondition,
        String changeReason
) {
    public PendingProductApplicationRequest(
            String applicationType, String productCode, String productName, String specModel,
            String brand, String manufacturerName, String supplierName, String unit,
            @jakarta.validation.constraints.DecimalMin(value = "0", message = "采购价不能为负数") BigDecimal purchasePrice, BigDecimal retailPrice, @com.hospital.spd.common.WholeQuantity BigDecimal minPurchaseQty,
            String purchaseUnit, @com.hospital.spd.common.WholeQuantity BigDecimal conversionRate, @com.hospital.spd.common.WholeQuantity BigDecimal purchasePackageQty,
            String udiCode, String registrationNo, String registrationExpireDate,
            String productionLicenseNo, String businessLicenseNo, Boolean volumeBased,
            Boolean centralizedProcurement, Boolean domestic, String contractCode,
            String firstCategory, String secondCategory, String thirdCategory, Boolean chargeable,
            String tenderSubCode, Integer qualificationAttachmentCount, Boolean highValue,
            Boolean coldChain, Boolean quotaManaged, String storageCondition, String changeReason
    ) {
        this(applicationType, productCode, productName, specModel, brand, manufacturerName,
                supplierName, unit, purchasePrice, retailPrice, minPurchaseQty, purchaseUnit,
                conversionRate, purchasePackageQty, udiCode, registrationNo,
                registrationExpireDate, productionLicenseNo, businessLicenseNo, volumeBased,
                centralizedProcurement, domestic, contractCode, firstCategory, secondCategory,
                thirdCategory, chargeable, tenderSubCode, qualificationAttachmentCount,
                highValue, coldChain, quotaManaged, false, storageCondition, changeReason);
    }
}
