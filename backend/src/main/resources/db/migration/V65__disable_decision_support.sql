UPDATE `sys_permission`
   SET `status` = 0,
       `deleted` = 1,
       `update_time` = CURRENT_TIMESTAMP
 WHERE `perm_code` = 'decision-screen'
   AND `deleted` = 0;
