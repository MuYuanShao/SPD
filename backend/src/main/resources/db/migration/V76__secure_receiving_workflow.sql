ALTER TABLE warehouse
  ADD COLUMN receiving_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许收货验收入库' AFTER status;

UPDATE warehouse
   SET receiving_enabled = 1
 WHERE deleted = 0
   AND (warehouse_type LIKE '%中心%'
        OR warehouse_type LIKE '%一级%'
        OR warehouse_type LIKE '%冷库%');

UPDATE sys_field_option
   SET status = 0
 WHERE field_key = 'receiving_type'
   AND option_value IN ('return', 'exchange');

UPDATE approval_flow
   SET data_scope = 1,
       remark = '中心库及配置收货库房验收审批'
 WHERE feature_code = 'receiving-acceptance'
   AND node_code = 'receiving-approval'
   AND scope_type = 'global'
   AND scope_id = 'default'
   AND deleted = 0;

UPDATE approval_flow_step step_cfg
  JOIN approval_flow flow_cfg ON flow_cfg.flow_id = step_cfg.flow_id
   SET step_cfg.data_scope = 1
 WHERE flow_cfg.feature_code = 'receiving-acceptance'
   AND flow_cfg.node_code = 'receiving-approval'
   AND flow_cfg.scope_type = 'global'
   AND flow_cfg.scope_id = 'default'
   AND flow_cfg.deleted = 0;

INSERT IGNORE INTO sys_permission
  (parent_id, perm_name, perm_code, perm_type, path, sort_order)
SELECT parent_perm.perm_id, action_def.perm_name, action_def.perm_code, 2, NULL, action_def.sort_order
  FROM sys_permission parent_perm
  JOIN (
    SELECT '查看收货验收' perm_name, 'receiving-order:read' perm_code, 331 sort_order UNION ALL
    SELECT '创建收货单', 'receiving-order:create', 332 UNION ALL
    SELECT '修改收货单', 'receiving-order:update', 333 UNION ALL
    SELECT '审批收货单', 'receiving-order:approve', 334 UNION ALL
    SELECT '拒收收货单', 'receiving-order:reject', 335
  ) action_def
 WHERE parent_perm.perm_code = 'receiving-acceptance'
   AND parent_perm.deleted = 0;

INSERT IGNORE INTO sys_role_perm (role_id, perm_id)
SELECT existing_write.role_id, action_perm.perm_id
  FROM sys_role_perm existing_write
  JOIN sys_permission write_perm
    ON write_perm.perm_id = existing_write.perm_id
   AND write_perm.perm_code = 'receiving-acceptance:write'
   AND write_perm.deleted = 0
  CROSS JOIN sys_permission action_perm
 WHERE action_perm.deleted = 0
   AND action_perm.perm_code LIKE 'receiving-order:%';
