package com.hospital.spd.licenses;

import java.math.BigDecimal;

public record LicenseUpsertRequest(
        String licenseType,
        String licenseName,
        String licenseNo,
        String ownerType,
        Long ownerId,
        String ownerCode,
        String ownerName,
        String partyA,
        String partyB,
        BigDecimal contractAmount,
        String issueDate,
        String expireDate,
        Integer status,
        String remark,
        Integer revisionNo
) {
}
