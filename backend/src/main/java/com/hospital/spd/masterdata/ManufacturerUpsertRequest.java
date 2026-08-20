package com.hospital.spd.masterdata;

public record ManufacturerUpsertRequest(
        String manufacturerCode,
        String manufacturerName,
        String creditCode,
        String licenseNo,
        String contactName,
        String contactPhone,
        String address
) {
}
