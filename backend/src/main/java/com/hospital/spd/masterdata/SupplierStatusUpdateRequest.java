package com.hospital.spd.masterdata;

import java.util.List;

public record SupplierStatusUpdateRequest(
        List<String> supplierCodes,
        Integer status
) {
}
