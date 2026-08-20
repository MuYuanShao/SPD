package com.hospital.spd.masterdata;

import java.util.List;

public record DepartmentWarehouseCatalogStatusRequest(List<Long> catalogIds, Integer status) {
}
