package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/** Enforces purchase button permissions at the business-service boundary. */
final class PurchasePermissionGuard {

    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;

    PurchasePermissionGuard(JdbcTemplate jdbcTemplate, OperatorContextProvider operatorContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
    }

    void require(String permissionCode) {
        OperatorContext operator = operatorContextProvider.current();
        if (operator.roles().stream().anyMatch(PurchasePermissionGuard::isAdministrator)) {
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
            throw new AccessDeniedException("无权限执行当前采购操作：" + permissionCode);
        }
    }

    void requireOrderScope(String orderNo) {
        requireOrderScope("po.order_no", orderNo);
    }

    void requireOrderIdScope(Long orderId) {
        requireOrderScope("po.purchase_order_id", orderId);
    }

    private void requireOrderScope(String column, Object id) {
        if (operatorContextProvider.current().canViewAllData()) return;
        StringBuilder where = new StringBuilder(" WHERE " + column + " = ?");
        java.util.List<Object> args = new java.util.ArrayList<>(); args.add(id);
        new com.hospital.spd.common.DataScopeService(operatorContextProvider).appendScope(where, args,
                "(SELECT owner.dept_id FROM sys_user owner WHERE owner.user_id = po.create_by)", "po.create_by");
        Long visible = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchase_order po" + where, Long.class, args.toArray());
        if (visible == null || visible != 1) throw new AccessDeniedException("无权访问该采购单");
    }

    private static boolean isAdministrator(String role) {
        return "admin".equalsIgnoreCase(role)
                || "ROLE_ADMIN".equalsIgnoreCase(role)
                || "ROLE_SYSTEM".equalsIgnoreCase(role);
    }
}
