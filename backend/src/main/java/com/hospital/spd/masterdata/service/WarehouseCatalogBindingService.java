package com.hospital.spd.masterdata.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Keeps warehouse-product bindings and generated department catalog rows consistent. */
@Service
public class WarehouseCatalogBindingService {

    private final JdbcTemplate jdbcTemplate;

    public WarehouseCatalogBindingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BindingResult ensureProductBinding(Long warehouseId, Long deptId, Long productId) {
        Integer active = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM warehouse_product_binding
                 WHERE warehouse_id = ? AND product_id = ? AND deleted = 0 AND status = 1
                """, Integer.class, warehouseId, productId);
        boolean created = active == null || active == 0;
        jdbcTemplate.update("""
                INSERT INTO warehouse_product_binding (warehouse_id, product_id, status, deleted)
                VALUES (?, ?, 1, 0)
                ON DUPLICATE KEY UPDATE status = 1, deleted = 0
                """, warehouseId, productId);
        synchronizeDepartmentCatalog(warehouseId, deptId);
        return new BindingResult(created, deptId != null);
    }

    public void synchronizeDepartmentCatalog(Long warehouseId, Long deptId) {
        List<Long> productIds = jdbcTemplate.queryForList("""
                SELECT product_id FROM warehouse_product_binding
                 WHERE warehouse_id = ? AND deleted = 0 AND status = 1
                """, Long.class, warehouseId);
        synchronizeDepartmentCatalog(warehouseId, deptId, productIds);
    }

    public void synchronizeDepartmentCatalog(Long warehouseId, Long deptId, List<Long> productIds) {
        jdbcTemplate.update("""
                UPDATE department_warehouse_catalog SET status = 0
                 WHERE warehouse_id = ? AND source_type = 'warehouse_binding' AND deleted = 0
                   AND (? IS NULL OR dept_id <> ?)
                """, warehouseId, deptId, deptId);
        if (deptId == null || productIds.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE department_warehouse_catalog SET status = 0
                     WHERE warehouse_id = ? AND source_type = 'warehouse_binding' AND deleted = 0
                    """, warehouseId);
            return;
        }
        String placeholders = String.join(",", productIds.stream().map(id -> "?").toList());
        List<Object> deactivate = new ArrayList<>(List.of(warehouseId, deptId));
        deactivate.addAll(productIds);
        jdbcTemplate.update(("""
                UPDATE department_warehouse_catalog SET status = 0
                 WHERE warehouse_id = ? AND dept_id = ? AND source_type = 'warehouse_binding'
                   AND deleted = 0 AND product_id NOT IN (%s)
                """).formatted(placeholders), deactivate.toArray());
        List<Object> activate = new ArrayList<>(List.of(warehouseId, deptId));
        activate.addAll(productIds);
        jdbcTemplate.update(("""
                UPDATE department_warehouse_catalog SET status = 1
                 WHERE warehouse_id = ? AND dept_id = ? AND source_type = 'warehouse_binding'
                   AND deleted = 0 AND product_id IN (%s)
                """).formatted(placeholders), activate.toArray());
        List<Object> insert = new ArrayList<>(List.of(deptId, warehouseId));
        insert.addAll(productIds);
        jdbcTemplate.update(("""
                INSERT IGNORE INTO department_warehouse_catalog
                  (dept_id, warehouse_id, product_id, source_type, status, deleted)
                SELECT ?, ?, product_id, 'warehouse_binding', 1, 0 FROM product
                 WHERE product_id IN (%s) AND deleted = 0 AND status = 1
                """).formatted(placeholders), insert.toArray());
    }

    public void deactivateWarehouse(Long warehouseId) {
        jdbcTemplate.update("UPDATE warehouse_product_binding SET status = 0 WHERE warehouse_id = ? AND deleted = 0", warehouseId);
        jdbcTemplate.update("UPDATE department_warehouse_catalog SET status = 0 WHERE warehouse_id = ? AND source_type = 'warehouse_binding' AND deleted = 0", warehouseId);
    }

    public record BindingResult(boolean bindingCreated, boolean catalogUpdated) {}
}
