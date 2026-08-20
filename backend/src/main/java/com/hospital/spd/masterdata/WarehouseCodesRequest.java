package com.hospital.spd.masterdata;

import java.util.List;

public record WarehouseCodesRequest(
        List<String> warehouseCodes
) {
}
