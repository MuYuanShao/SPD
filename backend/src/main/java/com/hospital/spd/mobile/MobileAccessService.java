package com.hospital.spd.mobile;

import com.hospital.spd.common.OperatorContext;
import com.hospital.spd.common.OperatorContextProvider;
import com.hospital.spd.supplychain.service.DepartmentRequisitionAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;

/** Enforces the mobile feature gate, existing signing permission and department warehouse scope. */
@Service
public class MobileAccessService {
    private final JdbcTemplate jdbc;
    private final OperatorContextProvider operators;
    private final DepartmentRequisitionAccessService access;
    private final boolean enabled;
    private final String serverId;

    public MobileAccessService(JdbcTemplate jdbc, OperatorContextProvider operators,
                               DepartmentRequisitionAccessService access,
                               @Value("${spd.mobile.enabled:false}") boolean enabled,
                               @Value("${spd.mobile.server-id:}") String serverId) {
        this.jdbc = jdbc;
        this.operators = operators;
        this.access = access;
        this.enabled = enabled;
        this.serverId = serverId;
        if (enabled && serverId.isBlank()) throw new IllegalStateException("启用移动作业必须配置 spd.mobile.server-id");
    }

    public OperatorContext operator() {
        OperatorContext operator = operators.current();
        if (operator.userId() == null) throw new AccessDeniedException("请先登录");
        return operator;
    }

    public void requireSigning() {
        operator();
        if (!enabled) throw new AccessDeniedException("移动作业尚未启用");
        access.requirePermission("picking-delivery:write");
    }

    public void requireWarehouse(Long deptId, Long warehouseId) {
        requireSigning();
        access.requireDepartment(deptId);
        access.requireDestinationWarehouse(deptId, warehouseId);
        Long active = jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept WHERE dept_id = ? AND deleted = 0 AND status = 1",
                Long.class, deptId);
        if (active == null || active != 1) throw new AccessDeniedException("科室已停用");
    }

    public MobileContracts.Context context() {
        OperatorContext operator = operator();
        try {
            requireSigning();
        } catch (AccessDeniedException ex) {
            return new MobileContracts.Context(serverId, operator.userId(), operator.username(), List.of(), List.of());
        }
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE w.deleted = 0 AND w.status = 1 AND d.deleted = 0 AND d.status = 1");
        // Match requireDepartment's SELF semantics for a warehouse choice, which has no document owner.
        if (Integer.valueOf(OperatorContext.DATA_SCOPE_SELF).equals(operator.dataScope())) {
            where.append(" AND d.dept_id = ?");
            args.add(operator.deptId());
        } else {
            access.appendScope(where, args, "d.dept_id", null);
        }
        List<MobileContracts.Warehouse> warehouses = jdbc.query("""
                SELECT d.dept_id, d.dept_name, w.warehouse_id, w.warehouse_name
                FROM warehouse w JOIN sys_dept d ON d.dept_id = w.dept_id
                """ + where + " ORDER BY d.dept_id, w.warehouse_id", (rs, row) -> new MobileContracts.Warehouse(
                rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)), args.toArray());
        return new MobileContracts.Context(serverId, operator.userId(), operator.username(),
                List.of("SIGN_DELIVERY"), warehouses);
    }
}
