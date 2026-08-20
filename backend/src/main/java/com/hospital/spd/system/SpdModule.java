package com.hospital.spd.system;

import java.util.List;

public record SpdModule(
        String code,
        String name,
        List<String> features
) {
}
