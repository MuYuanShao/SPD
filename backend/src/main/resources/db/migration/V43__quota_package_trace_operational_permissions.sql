INSERT INTO `sys_permission`
    (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`, `status`, `deleted`)
VALUES
    (0, 'UDI追溯', 'udi-traceability', 1, '/features/udi-traceability', 40, 1, 0),
    (0, 'UDI追溯写操作', 'udi-traceability:write', 2, NULL, 40, 1, 0)
ON DUPLICATE KEY UPDATE
    `status` = 1,
    `deleted` = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id
  FROM `sys_role` r
  JOIN `sys_permission` p
    ON p.perm_code IN ('udi-traceability', 'udi-traceability:write')
 WHERE r.role_code IN ('warehouse', 'dept_user')
   AND r.deleted = 0
   AND r.status = 1
   AND p.deleted = 0
   AND p.status = 1;
