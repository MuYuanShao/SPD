package com.hospital.spd.masterdata;

import java.math.BigDecimal;

public record ProductCreateRequest(
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
        boolean volumeBased,
        boolean centralizedProcurement,
        boolean domestic,
        String contractCode,
        String firstCategory,
        String secondCategory,
        String thirdCategory,
        boolean chargeable,
        String tenderSubCode,
        boolean highValue,
        boolean coldChain,
        boolean quotaManaged,
        boolean keyMonitored,
        String storageCondition
) {
    public ProductCreateRequest(
            String productCode, String productName, String specModel, String brand,
            String manufacturerName, String supplierName, String unit, BigDecimal purchasePrice,
            BigDecimal retailPrice, BigDecimal minPurchaseQty, String purchaseUnit,
            BigDecimal conversionRate, BigDecimal purchasePackageQty, String udiCode,
            String registrationNo, String registrationExpireDate, String productionLicenseNo,
            String businessLicenseNo, boolean volumeBased, boolean centralizedProcurement,
            boolean domestic, String contractCode, String firstCategory, String secondCategory,
            String thirdCategory, boolean chargeable, String tenderSubCode, boolean highValue,
            boolean coldChain, boolean quotaManaged, String storageCondition
    ) {
        this(productCode, productName, specModel, brand, manufacturerName, supplierName, unit,
                purchasePrice, retailPrice, minPurchaseQty, purchaseUnit, conversionRate,
                purchasePackageQty, udiCode, registrationNo, registrationExpireDate,
                productionLicenseNo, businessLicenseNo, volumeBased, centralizedProcurement,
                domestic, contractCode, firstCategory, secondCategory, thirdCategory, chargeable,
                tenderSubCode, highValue, coldChain, quotaManaged, false, storageCondition);
    }
}
