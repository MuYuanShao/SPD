package com.hospital.spd.masterdata.service;

import com.hospital.spd.masterdata.DepartmentWarehouseRelationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Single transactional policy for assigning department-owned warehouses. */
@Service
public class WarehouseDepartmentAssignmentService {
    private final JdbcTemplate jdbcTemplate;
    private final WarehouseCatalogBindingService catalogBindingService;

    public WarehouseDepartmentAssignmentService(JdbcTemplate jdbcTemplate,
                                                WarehouseCatalogBindingService catalogBindingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.catalogBindingService = catalogBindingService;
    }

    public Map<String, Object> replace(Long deptId, DepartmentWarehouseRelationRequest request) {
        List<String> codes = request == null || request.warehouseCodes() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.warehouseCodes().stream()
                .filter(code -> code != null && !code.isBlank()).map(String::trim).toList()));
        List<Map<String, Object>> current = jdbcTemplate.queryForList("""
                SELECT warehouse_id, warehouse_code, dept_id FROM warehouse
                 WHERE deleted = 0 AND dept_id = ?
                """, deptId);
        List<Map<String, Object>> selected = codes.isEmpty() ? List.of() : jdbcTemplate.queryForList(("""
                SELECT warehouse_id, warehouse_code, warehouse_type, dept_id FROM warehouse
                 WHERE deleted = 0 AND status = 1 AND warehouse_code IN (%s)
                """).formatted(placeholders(codes.size())), codes.toArray());
        if (selected.size() != codes.size()) {
            throw new IllegalArgumentException("所选库房中存在不存在、已删除或已停用的记录");
        }
        for (Map<String, Object> row : selected) {
            String type = String.valueOf(row.get("warehouse_type"));
            if (!WarehouseDepartmentPolicy.requiresDepartment(type)) {
                throw new IllegalArgumentException("库房“" + row.get("warehouse_code") + "”不是可归属科室的库房类型");
            }
            Object owner = row.get("dept_id");
            if (owner instanceof Number number && number.longValue() != deptId) {
                throw new IllegalArgumentException("库房“" + row.get("warehouse_code") + "”已归属其他科室");
            }
        }
        int updated = 0;
        for (Map<String, Object> row : current) {
            String code = String.valueOf(row.get("warehouse_code"));
            if (!codes.contains(code)) {
                Long warehouseId = ((Number) row.get("warehouse_id")).longValue();
                updated += jdbcTemplate.update("UPDATE warehouse SET dept_id = NULL WHERE warehouse_id = ? AND dept_id = ?", warehouseId, deptId);
                catalogBindingService.synchronizeDepartmentCatalog(warehouseId, null);
            }
        }
        for (Map<String, Object> row : selected) {
            Long warehouseId = ((Number) row.get("warehouse_id")).longValue();
            updated += jdbcTemplate.update("UPDATE warehouse SET dept_id = ? WHERE warehouse_id = ? AND deleted = 0", deptId, warehouseId);
            catalogBindingService.synchronizeDepartmentCatalog(warehouseId, deptId);
        }
        return Map.of("updatedRows", updated, "warehouseCount", selected.size());
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }
}
