package com.hospital.spd.masterdata.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Performs fail-before-write reference checks for organization master-data deletion. */
@Service
public class MasterDataReferenceGuard {
    private final JdbcTemplate jdbcTemplate;

    public MasterDataReferenceGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireDepartmentsDeletable(List<String> codes) {
        String in = placeholders(codes.size());
        List<Map<String, Object>> blocked = jdbcTemplate.queryForList(("""
                SELECT d.dept_code AS objectCode, d.dept_name AS objectName,
                       CASE
                         WHEN EXISTS (SELECT 1 FROM sys_dept c WHERE c.parent_id=d.dept_id AND c.deleted=0 AND c.status=1) THEN '存在活动子科室'
                         WHEN EXISTS (SELECT 1 FROM sys_user u WHERE u.dept_id=d.dept_id AND u.deleted=0 AND u.status=1) THEN '存在启用用户'
                         WHEN EXISTS (SELECT 1 FROM warehouse w WHERE w.dept_id=d.dept_id AND w.deleted=0 AND w.status=1) THEN '存在活动库房'
                         WHEN EXISTS (SELECT 1 FROM department_requisition r WHERE r.dept_id=d.dept_id AND r.status NOT IN ('completed','cancelled','rejected')) THEN '存在未完结科室业务'
                       END AS reason
                  FROM sys_dept d WHERE d.deleted=0 AND d.dept_code IN (%s)
                HAVING reason IS NOT NULL
                """).formatted(in), codes.toArray());
        reject(blocked, "科室");
    }

    public void requireWarehousesDeletable(List<String> codes) {
        String in = placeholders(codes.size());
        List<Map<String, Object>> blocked = jdbcTemplate.queryForList(("""
                SELECT w.warehouse_code AS objectCode, w.warehouse_name AS objectName,
                       CASE
                         WHEN EXISTS (SELECT 1 FROM inventory_balance b WHERE b.warehouse_id=w.warehouse_id
                           AND (b.available_qty<>0 OR b.locked_qty<>0 OR b.in_transit_qty<>0 OR b.isolated_qty<>0)) THEN '存在非零库存或锁定/在途/隔离数量'
                         WHEN EXISTS (SELECT 1 FROM receiving_order r WHERE r.warehouse_id=w.warehouse_id
                           AND r.receiving_status NOT IN ('completed','cancelled','rejected')) THEN '存在未完结收货单'
                         WHEN EXISTS (SELECT 1 FROM inventory_stocktaking s WHERE s.warehouse_id=w.warehouse_id
                           AND s.status NOT IN ('completed','cancelled','rejected')) THEN '存在未完结盘点单'
                         WHEN EXISTS (SELECT 1 FROM quota_packing_task q WHERE q.warehouse_id=w.warehouse_id
                           AND q.status NOT IN ('completed','cancelled','rejected')) THEN '存在未完结打包任务'
                         WHEN EXISTS (SELECT 1 FROM quota_package_label l WHERE l.warehouse_id=w.warehouse_id
                           AND l.status NOT IN ('used','cancelled','scrapped')) THEN '存在在用定数包'
                       END AS reason
                  FROM warehouse w WHERE w.deleted=0 AND w.warehouse_code IN (%s)
                HAVING reason IS NOT NULL
                """).formatted(in), codes.toArray());
        reject(blocked, "库房");
    }

    public void requireLocationDeletable(Long warehouseId, Long locationId) {
        List<Map<String, Object>> blocked = jdbcTemplate.queryForList("""
                SELECT wl.location_code AS objectCode, wl.location_code AS objectName,
                       '存在非零库存' AS reason
                  FROM warehouse_location wl
                 WHERE wl.warehouse_id=? AND wl.location_id=? AND wl.deleted=0
                   AND EXISTS (SELECT 1 FROM inventory_balance b WHERE b.location_id=wl.location_id
                     AND (b.available_qty<>0 OR b.locked_qty<>0 OR b.in_transit_qty<>0 OR b.isolated_qty<>0))
                """, warehouseId, locationId);
        reject(blocked, "货位");
    }

    private static void reject(List<Map<String, Object>> rows, String type) {
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            throw new IllegalArgumentException(type + "“" + row.get("objectName") + "”无法删除：" + row.get("reason"));
        }
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }
}
