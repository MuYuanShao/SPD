package com.hospital.spd.supplychain;

import java.util.List;

public record StocktakingSheetRequest(
        String warehouseName,
        String deptName,
        List<String> scopes
) {
}
