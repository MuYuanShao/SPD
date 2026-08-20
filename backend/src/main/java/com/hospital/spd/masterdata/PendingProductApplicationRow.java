package com.hospital.spd.masterdata;

import java.math.BigDecimal;

public record PendingProductApplicationRow(
        String no,
        String type,
        String product,
        String supplier,
        String applicant,
        String time,
        String status,
        String statusTone,
        boolean warning,
        String productCode,
        String changeSummary,
        String manufacturerName,
        String registrationNo,
        String contractCode,
        String firstCategory,
        String secondCategory,
        String thirdCategory,
        Boolean volumeBased,
        Boolean centralizedProcurement,
        Boolean domestic,
        Boolean chargeable,
        BigDecimal purchasePrice,
        String purchaseUnit,
        String udiCode,
        Boolean quotaManaged
) {
    public PendingProductApplicationRow(
            String no,
            String type,
            String product,
            String supplier,
            String applicant,
            String time,
            String status,
            String statusTone,
            boolean warning,
            String productCode,
            String changeSummary
    ) {
        this(no, type, product, supplier, applicant, time, status, statusTone, warning, productCode, changeSummary,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
