ALTER TABLE `purchase_demand`
  ADD COLUMN `create_by` BIGINT UNSIGNED NULL COMMENT '创建人' AFTER `remark`,
  ADD KEY `idx_purchase_demand_owner_dept` (`create_by`, `dept_id`, `demand_status`),
  ADD KEY `idx_purchase_demand_no_status` (`demand_no`, `demand_status`);

INSERT IGNORE INTO `sys_permission`
  (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`)
SELECT parent_perm.perm_id, action_def.perm_name, action_def.perm_code, 2, NULL, action_def.sort_order
  FROM `sys_permission` parent_perm
  JOIN (
    SELECT '创建采购需求' perm_name, 'purchase-demand:create' perm_code, 301 sort_order UNION ALL
    SELECT '提交采购需求', 'purchase-demand:submit', 302 UNION ALL
    SELECT '审核采购需求', 'purchase-demand:review', 303 UNION ALL
    SELECT '驳回采购需求', 'purchase-demand:reject', 304 UNION ALL
    SELECT '需求转采购计划', 'purchase-demand:convert-plan', 305 UNION ALL
    SELECT '审核采购计划', 'purchase-plan:approve', 311 UNION ALL
    SELECT '驳回采购计划', 'purchase-plan:reject', 312 UNION ALL
    SELECT '计划转采购订单', 'purchase-plan:execute', 313 UNION ALL
    SELECT '创建采购订单', 'purchase-order:create', 321 UNION ALL
    SELECT '提交采购订单', 'purchase-order:submit', 322 UNION ALL
    SELECT '审批采购订单', 'purchase-order:approve', 323 UNION ALL
    SELECT '驳回采购订单', 'purchase-order:reject', 324 UNION ALL
    SELECT '发送采购订单', 'purchase-order:send', 325 UNION ALL
    SELECT '关闭采购订单', 'purchase-order:close', 326 UNION ALL
    SELECT '作废采购订单', 'purchase-order:void', 327 UNION ALL
    SELECT '采购订单备注', 'purchase-order:remark', 328 UNION ALL
    SELECT '采购订单附件', 'purchase-order:attachment', 329
  ) action_def
 WHERE parent_perm.perm_code = 'purchase-management'
   AND parent_perm.deleted = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT existing_write.role_id, action_perm.perm_id
  FROM `sys_role_perm` existing_write
  JOIN `sys_permission` write_perm
    ON write_perm.perm_id = existing_write.perm_id
   AND write_perm.perm_code = 'purchase-management:write'
   AND write_perm.deleted = 0
  CROSS JOIN `sys_permission` action_perm
 WHERE action_perm.deleted = 0
   AND (action_perm.perm_code LIKE 'purchase-demand:%'
        OR action_perm.perm_code LIKE 'purchase-plan:%'
        OR action_perm.perm_code LIKE 'purchase-order:%');
