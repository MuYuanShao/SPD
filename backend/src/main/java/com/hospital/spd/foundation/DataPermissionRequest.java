package com.hospital.spd.foundation;

import java.util.List;

public record DataPermissionRequest(
        Long roleId,
        Integer dataScope,
        List<Long> deptIds
) {
}
