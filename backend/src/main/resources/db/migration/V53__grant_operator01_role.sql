-- 修复运营管理员种子账号缺失角色分配：operator01（运营管理员）无任何角色导致零权限
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.user_id, r.role_id
  FROM sys_user u
  JOIN sys_role r ON r.role_code = 'operator' AND r.deleted = 0 AND r.status = 1
 WHERE u.username = 'operator01' AND u.deleted = 0;
