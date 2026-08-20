package com.hospital.spd.masterdata;

public record SupplierUpsertRequest(
        String supplierCode,
        String supplierName,
        String creditCode,
        String businessLicenseNo,
        String supplierType,
        String grade,
        String contactName,
        String contactPhone,
        String email,
        String address
) {
}
