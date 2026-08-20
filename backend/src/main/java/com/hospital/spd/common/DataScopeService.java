package com.hospital.spd.common;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Appends fail-closed data-scope predicates for department-owned or user-owned queries.
 */
@Service
public class DataScopeService {

    private final OperatorContextProvider operatorContextProvider;

    public DataScopeService(OperatorContextProvider operatorContextProvider) {
        this.operatorContextProvider = operatorContextProvider;
    }

    public void appendScope(StringBuilder where, List<Object> args, String deptColumn, String ownerColumn) {
        OperatorContext operator = operatorContextProvider.current();
        if (operator.canViewAllData()) {
            return;
        }
        Integer scope = operator.dataScope();
        if (scope == OperatorContext.DATA_SCOPE_SELF && hasText(ownerColumn)) {
            appendOwnerScope(where, args, ownerColumn, operator.userId());
            return;
        }
        if (scope == OperatorContext.DATA_SCOPE_DEPT && hasText(deptColumn)) {
            appendDeptScope(where, args, deptColumn, operator.deptId());
            return;
        }
        if (scope == OperatorContext.DATA_SCOPE_DEPT_AND_CHILDREN && hasText(deptColumn)) {
            appendDeptTreeScope(where, args, deptColumn, operator.deptId());
            return;
        }
        if (scope == OperatorContext.DATA_SCOPE_CUSTOM && hasText(deptColumn)) {
            where.append(" AND ").append(deptColumn).append(" IN (SELECT rd.dept_id FROM sys_role_dept rd JOIN sys_user_role ur ON ur.role_id = rd.role_id WHERE ur.user_id = ?)");
            args.add(operator.userId());
            return;
        }
        where.append(" AND 1 = 0");
    }

    private static void appendOwnerScope(StringBuilder where, List<Object> args, String ownerColumn, Long userId) {
        if (userId == null) {
            where.append(" AND 1 = 0");
            return;
        }
        where.append(" AND ").append(ownerColumn).append(" = ?");
        args.add(userId);
    }

    private static void appendDeptScope(StringBuilder where, List<Object> args, String deptColumn, Long deptId) {
        if (deptId == null) {
            where.append(" AND 1 = 0");
            return;
        }
        where.append(" AND ").append(deptColumn).append(" = ?");
        args.add(deptId);
    }

    private static void appendDeptTreeScope(StringBuilder where, List<Object> args, String deptColumn, Long deptId) {
        if (deptId == null) {
            where.append(" AND 1 = 0");
            return;
        }
        where.append(" AND (")
                .append(deptColumn)
                .append(" = ? OR ")
                .append(deptColumn)
                .append(" IN (SELECT dept_id FROM sys_dept WHERE parent_id = ? AND deleted = 0))");
        args.add(deptId);
        args.add(deptId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
