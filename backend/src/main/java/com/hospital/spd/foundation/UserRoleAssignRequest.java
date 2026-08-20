package com.hospital.spd.foundation;

import java.util.List;

public record UserRoleAssignRequest(
        Long userId,
        List<Long> roleIds
) {
}
