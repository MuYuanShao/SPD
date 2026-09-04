package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/** Enforces receiving action permissions at the service boundary. */
final class ReceivingPermissionGuard {

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;

    ReceivingPermissionGuard(JdbcTemplate jdbcTemplate, OperatorContextProvider operatorContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
    }

    void require(String permissionCode) {
        OperatorContext operator = operatorContextProvider.current();
        if (operator.roles().stream().anyMatch(ReceivingPermissionGuard::isAdministrator)) {
            return;
        }
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT p.perm_id)
                  FROM sys_user_role ur
                  JOIN sys_role r ON r.role_id = ur.role_id AND r.deleted = 0 AND r.status = 1
                  JOIN sys_role_perm rp ON rp.role_id = r.role_id
                  JOIN sys_permission p ON p.perm_id = rp.perm_id AND p.deleted = 0 AND p.status = 1
                 WHERE ur.user_id = ? AND p.perm_code = ?
                """, Long.class, operator.userId(), permissionCode);
        if (count == null || count == 0) {
            throw new AccessDeniedException("无权限执行当前收货操作：" + permissionCode);
        }
    }

    private static boolean isAdministrator(String role) {
        return "admin".equalsIgnoreCase(role)
                || "ROLE_ADMIN".equalsIgnoreCase(role)
                || "ROLE_SYSTEM".equalsIgnoreCase(role);
    }
}
