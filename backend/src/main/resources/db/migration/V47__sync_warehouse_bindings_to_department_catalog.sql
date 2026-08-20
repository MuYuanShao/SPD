-- Backfill department warehouse catalogs from active warehouse product bindings.
-- Existing manual and other source rows win through the active-row unique key.
INSERT IGNORE INTO `department_warehouse_catalog` (
  `dept_id`, `warehouse_id`, `product_id`, `source_type`, `status`, `deleted`
)
SELECT w.dept_id, b.warehouse_id, b.product_id, 'warehouse_binding', 1, 0
  FROM warehouse_product_binding b
  JOIN warehouse w ON w.warehouse_id = b.warehouse_id
 WHERE w.deleted = 0
   AND w.dept_id IS NOT NULL
   AND b.deleted = 0
   AND b.status = 1;
