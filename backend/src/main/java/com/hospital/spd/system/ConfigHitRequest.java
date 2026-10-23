package com.hospital.spd.system;

public record ConfigHitRequest(
        String configType,
        String configKey,
        String businessPage,
        String userId,
        String moduleId,
        String warehouseId,
        String departmentId,
        String campusId,
        String hospitalId,
        String tenantId
) {
}
