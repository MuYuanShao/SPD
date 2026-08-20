package com.hospital.spd.masterdata;

import java.util.List;

public record ManufacturerStatusUpdateRequest(
        List<String> manufacturerCodes,
        Integer status
) {
}
