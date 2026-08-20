package com.hospital.spd.masterdata;

import java.util.List;

public record ProductStatusUpdateRequest(
        List<String> productCodes,
        Integer status
) {
}
