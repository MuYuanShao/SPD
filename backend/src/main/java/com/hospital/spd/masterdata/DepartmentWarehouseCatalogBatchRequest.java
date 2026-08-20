package com.hospital.spd.masterdata;

import java.util.List;

public record DepartmentWarehouseCatalogBatchRequest(
        String deptName,
        String warehouseName,
        List<String> productCodes,
        Integer status
) {
}
