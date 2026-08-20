package com.hospital.spd.common;

import java.util.List;

/**
 * Captures the authenticated operator data shared by security, audit, approval, and data-scope code.
 */
public record OperatorContext(
        Long userId,
        String username,
        String ipAddress,
        List<String> roles,
        Long deptId,
        Integer dataScope
) {
    public static final int DATA_SCOPE_ALL = 1;
    public static final int DATA_SCOPE_DEPT = 2;
    public static final int DATA_SCOPE_DEPT_AND_CHILDREN = 3;
    public static final int DATA_SCOPE_SELF = 4;
    public static final int DATA_SCOPE_CUSTOM = 5;

    private static final OperatorContext SYSTEM = new OperatorContext(
            1L,
            "system",
            "127.0.0.1",
            List.of("ROLE_SYSTEM"),
            null,
            DATA_SCOPE_ALL
    );

    public static OperatorContext system() {
        return SYSTEM;
    }

    public boolean canViewAllData() {
        return dataScope == null || dataScope <= DATA_SCOPE_ALL;
    }
}
