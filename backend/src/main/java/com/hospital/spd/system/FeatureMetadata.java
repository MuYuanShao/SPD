package com.hospital.spd.system;

import java.util.List;

public record FeatureMetadata(
        String code,
        String title,
        String description,
        List<String> capabilities
) {
}
