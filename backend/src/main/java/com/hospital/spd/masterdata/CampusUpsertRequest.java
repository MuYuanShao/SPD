package com.hospital.spd.masterdata;

public record CampusUpsertRequest(
        String campusCode,
        String campusName,
        String address,
        String managerName,
        String phone,
        Integer sortOrder,
        Integer status
) {
}
