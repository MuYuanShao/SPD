package com.hospital.spd.foundation;

public record RoleRequest(
        String roleName,
        String roleCode,
        String description,
        Integer dataScope,
        Integer status,
        Integer sortOrder
) {
}
