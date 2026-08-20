-- Restore the retained operational roles' read-only inventory access.
-- This is intentionally idempotent so local databases whose role permissions were
-- edited during E2E authorization can recover the documented baseline.
INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id
  FROM `sys_role` r
  JOIN `sys_permission` p
    ON p.perm_code IN ('inventory-management', 'inventory-events')
 WHERE r.role_code IN ('operator', 'leader', 'auditor')
   AND r.deleted = 0
   AND r.status = 1
   AND p.deleted = 0
   AND p.status = 1;
