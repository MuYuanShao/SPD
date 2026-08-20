-- Repair environments where the RBAC history exists but the custom role-department table is absent.
CREATE TABLE IF NOT EXISTS `sys_role_dept` (
  `role_id` BIGINT UNSIGNED NOT NULL,
  `dept_id` BIGINT UNSIGNED NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`role_id`, `dept_id`),
  KEY `idx_role_dept_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色自定义数据权限部门';
