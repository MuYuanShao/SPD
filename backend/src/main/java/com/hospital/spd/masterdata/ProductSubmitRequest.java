package com.hospital.spd.masterdata;

import java.util.List;

public record ProductSubmitRequest(
        List<String> productCodes
) {
}
