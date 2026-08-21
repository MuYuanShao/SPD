package com.hospital.spd.masterdata;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetail(
        Long productId,
        String productCode,
        String productName,
        String specModel,
        String brand,
        String categoryName,
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
        String storageCondition,
        String statusLabel,
        List<ProductAttachment> attachments
) {
}
