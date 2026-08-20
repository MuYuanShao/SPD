INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.user_id, r.role_id
  FROM sys_user u
  JOIN sys_role r ON r.role_code = 'approver' AND r.deleted = 0
 WHERE u.username = 'admin'
   AND u.deleted = 0;
