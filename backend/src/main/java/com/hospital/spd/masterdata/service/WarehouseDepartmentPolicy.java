package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.WarehouseUpsertRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.hospital.spd.common.SqlHelper.isBlank;

/** Resolves and validates the single department owner of a warehouse. */
@Service
public class WarehouseDepartmentPolicy {

    private final JdbcTemplate jdbcTemplate;

    public WarehouseDepartmentPolicy(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long resolveDepartmentId(WarehouseUpsertRequest request) {
        String deptCode = trimToNull(request.deptCode());
        String deptName = trimToNull(request.deptName());
        if (deptCode != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT dept_id, dept_name
                      FROM sys_dept
                     WHERE dept_code = ? AND deleted = 0 AND status = 1
                    """, deptCode);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("科室不存在、已停用或已删除");
            }
            String actualName = String.valueOf(rows.get(0).get("dept_name"));
            if (deptName != null && !actualName.equals(deptName)) {
                throw new IllegalArgumentException("科室编码与科室名称不匹配");
            }
            return ((Number) rows.get(0).get("dept_id")).longValue();
        }

        if (deptName == null) {
            if (requiresDepartment(request.warehouseType())) {
                throw new IllegalArgumentException("二级库、三级库和科室库必须关联有效科室");
            }
            return null;
        }

        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT dept_id FROM sys_dept WHERE dept_name = ? AND deleted = 0 AND status = 1",
                Long.class, deptName);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("科室不存在、已停用或已删除");
        }
        if (ids.size() > 1) {
            throw new IllegalArgumentException("科室名称不唯一，请使用科室编码");
        }
        return ids.get(0);
    }

    public static boolean requiresDepartment(String warehouseType) {
        if (isBlank(warehouseType)) {
            return false;
        }
        String type = warehouseType.trim();
        return type.contains("二级") || type.contains("三级") || type.contains("科室库");
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
