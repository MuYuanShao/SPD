package com.hospital.spd.supplychain.service;

import com.hospital.spd.common.DataScopeService;
import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Enforces department-requisition permissions and department data scope at the service boundary. */
@Service
public class DepartmentRequisitionAccessService {
    private final JdbcTemplate jdbcTemplate;
    private final OperatorContextProvider operatorContextProvider;
    private final DataScopeService dataScopeService;

    @Autowired
    public DepartmentRequisitionAccessService(JdbcTemplate jdbcTemplate,
                                              OperatorContextProvider operatorContextProvider,
                                              DataScopeService dataScopeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorContextProvider = operatorContextProvider;
        this.dataScopeService = dataScopeService;
    }

    public DepartmentRequisitionAccessService(JdbcTemplate jdbcTemplate,
                                              OperatorContextProvider operatorContextProvider) {
        this(jdbcTemplate, operatorContextProvider, new DataScopeService(operatorContextProvider));
    }

    public void requirePermission(String permissionCode) {
        OperatorContext operator = operatorContextProvider.current();
        if (isAdministrator(operator)) return;
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT p.perm_id)
                  FROM sys_user_role ur
                  JOIN sys_role r ON r.role_id = ur.role_id AND r.deleted = 0 AND r.status = 1
                  JOIN sys_role_perm rp ON rp.role_id = r.role_id
                  JOIN sys_permission p ON p.perm_id = rp.perm_id AND p.deleted = 0 AND p.status = 1
                 WHERE ur.user_id = ? AND p.perm_code = ?
                """, Long.class, operator.userId(), permissionCode);
        if (count == null || count == 0) {
            throw new AccessDeniedException("无权限执行当前科室申领操作：" + permissionCode);
        }
    }

    public Map<String, Object> resolveDepartment(String deptCode, String deptName) {
        List<Map<String, Object>> rows;
        if (hasText(deptCode)) {
            rows = jdbcTemplate.queryForList("""
                    SELECT dept_id AS deptId, dept_code AS deptCode, dept_name AS deptName
                      FROM sys_dept WHERE dept_code = ? AND deleted = 0 AND status = 1
                    """, deptCode.trim());
            if (rows.size() != 1) throw new IllegalArgumentException("科室编码不存在或已停用");
            if (hasText(deptName) && !Objects.equals(deptName.trim(), String.valueOf(rows.get(0).get("deptName")))) {
                throw new IllegalArgumentException("科室编码与科室名称不匹配");
            }
        } else {
            if (!hasText(deptName)) throw new IllegalArgumentException("deptCode is required");
            rows = jdbcTemplate.queryForList("""
                    SELECT dept_id AS deptId, dept_code AS deptCode, dept_name AS deptName
                      FROM sys_dept WHERE dept_name = ? AND deleted = 0 AND status = 1
                    """, deptName.trim());
            if (rows.isEmpty()) throw new IllegalArgumentException("科室不存在或已停用");
            if (rows.size() != 1) throw new IllegalArgumentException("科室名称不唯一，请使用科室编码");
        }
        Map<String, Object> department = rows.get(0);
        requireDepartment(((Number) department.get("deptId")).longValue());
        return department;
    }

    public void requireDepartment(Long deptId) {
        OperatorContext operator = operatorContextProvider.current();
        if (operator.canViewAllData()) return;
        boolean allowed = switch (operator.dataScope()) {
            case OperatorContext.DATA_SCOPE_DEPT -> Objects.equals(operator.deptId(), deptId);
            case OperatorContext.DATA_SCOPE_DEPT_AND_CHILDREN -> isDepartmentOrDirectChild(operator.deptId(), deptId);
            case OperatorContext.DATA_SCOPE_CUSTOM -> isCustomDepartment(operator.userId(), deptId);
            case OperatorContext.DATA_SCOPE_SELF -> Objects.equals(operator.deptId(), deptId);
            default -> false;
        };
        if (!allowed) throw new AccessDeniedException("无权访问所选科室数据");
    }

    public void requireDestinationWarehouse(Long deptId, Long warehouseId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM warehouse
                 WHERE warehouse_id = ? AND dept_id = ? AND deleted = 0 AND status = 1
                """, Long.class, warehouseId, deptId);
        if (count == null || count != 1) throw new IllegalArgumentException("目标库房未关联所选科室或已停用");
    }

    public void appendScope(StringBuilder where, List<Object> args, String deptColumn, String ownerColumn) {
        dataScopeService.appendScope(where, args, deptColumn, ownerColumn);
    }

    public boolean isUnrestricted() {
        return operatorContextProvider.current().canViewAllData();
    }

    private boolean isDepartmentOrDirectChild(Long operatorDeptId, Long targetDeptId) {
        if (Objects.equals(operatorDeptId, targetDeptId)) return true;
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_dept
                 WHERE dept_id = ? AND parent_id = ? AND deleted = 0 AND status = 1
                """, Long.class, targetDeptId, operatorDeptId);
        return count != null && count == 1;
    }

    private boolean isCustomDepartment(Long userId, Long deptId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sys_role_dept rd JOIN sys_user_role ur ON ur.role_id = rd.role_id
                 WHERE ur.user_id = ? AND rd.dept_id = ?
                """, Long.class, userId, deptId);
        return count != null && count > 0;
    }

    private static boolean isAdministrator(OperatorContext operator) {
        return operator.roles().stream().anyMatch(role -> "admin".equalsIgnoreCase(role)
                || "ROLE_ADMIN".equalsIgnoreCase(role) || "ROLE_SYSTEM".equalsIgnoreCase(role));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
