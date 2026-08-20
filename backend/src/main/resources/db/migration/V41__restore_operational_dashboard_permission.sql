-- Keep retained operational users on a usable landing page after login.
INSERT INTO `sys_permission`
    (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`, `status`, `deleted`)
VALUES
    (0, '工作台', 'dashboard', 1, '/', 1, 1, 0)
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `perm_type` = VALUES(`perm_type`),
    `path` = VALUES(`path`),
    `sort_order` = VALUES(`sort_order`),
    `status` = 1,
    `deleted` = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id
  FROM `sys_role` r
  JOIN `sys_permission` p ON p.perm_code = 'dashboard'
 WHERE r.role_code IN ('operator', 'leader', 'auditor')
   AND r.deleted = 0
   AND r.status = 1
   AND p.deleted = 0
   AND p.status = 1;
