package com.hospital.spd.masterdata;

public record DepartmentWarehouseCatalogUpsertRequest(
        String deptName,
        String warehouseName,
        String productCode,
        Integer status
) {
}
