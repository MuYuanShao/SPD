package com.hospital.spd.foundation;

import java.util.List;

public record RolePermissionAssignRequest(
        Long roleId,
        List<Long> permissionIds
) {
}
