package com.hospital.spd.masterdata;

import java.util.List;

/**
 * Warehouses assigned to a department from the department management toolbar.
 */
public record DepartmentWarehouseRelationRequest(
        List<String> warehouseCodes
) {
}
