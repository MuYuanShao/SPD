-- Move the inventory product list into the report center with its own read permission.
INSERT IGNORE INTO sys_permission
    (parent_id, perm_name, perm_code, perm_type, path, component, icon, sort_order, status)
SELECT COALESCE(p.perm_id, 0), '进销存商品明细', 'inventory-product-detail-report', 1,
       '/features/inventory-product-detail-report', 'operations/InventoryProductDetailView', 'clipboard-list', 615, 1
FROM (SELECT 1 AS seed) seed
LEFT JOIN sys_permission p ON p.perm_code = 'report-center';

INSERT IGNORE INTO sys_role_perm (role_id, perm_id)
SELECT r.role_id, p.perm_id
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'inventory-product-detail-report'
WHERE r.role_code IN ('admin', 'operator', 'leader', 'auditor')
  AND r.deleted = 0 AND p.deleted = 0;
