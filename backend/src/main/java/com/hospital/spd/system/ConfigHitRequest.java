package com.hospital.spd.system;

public record ConfigHitRequest(
        String configType,
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
