package com.hospital.spd.masterdata;

import java.util.List;

public record WarehouseUpsertRequest(
        String warehouseCode,
        String warehouseName,
        String warehouseType,
        String campusName,
        String deptCode,
        String deptName,
        Boolean participateStats,
        String statsCategories,
        Integer status,
        List<String> productCodes
) {
    public WarehouseUpsertRequest(String warehouseCode, String warehouseName, String warehouseType,
                                  String campusName, String deptName, Boolean participateStats,
                                  String statsCategories, Integer status, List<String> productCodes) {
        this(warehouseCode, warehouseName, warehouseType, campusName, null, deptName,
                participateStats, statsCategories, status, productCodes);
    }
}
