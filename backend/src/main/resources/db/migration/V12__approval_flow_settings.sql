CREATE TABLE IF NOT EXISTS `approval_flow` (
  `flow_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批流ID',
  `feature_code` VARCHAR(80) NOT NULL COMMENT '功能编码',
  `feature_name` VARCHAR(100) NOT NULL COMMENT '功能名称',
  `node_code` VARCHAR(80) NOT NULL COMMENT '功能节点编码',
  `node_name` VARCHAR(100) NOT NULL COMMENT '功能节点名称',
  `scope_type` VARCHAR(30) NOT NULL DEFAULT 'global' COMMENT '适用范围：global/department/warehouse/role',
  `scope_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '适用对象',
  `data_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '数据隔离：1全部 2本部门 3本部门及下级 4本人',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '所属部门',
  `create_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`flow_id`),
  UNIQUE KEY `uk_approval_flow_node_scope` (`feature_code`, `node_code`, `scope_type`, `scope_id`),
  KEY `idx_approval_flow_feature` (`feature_code`, `node_code`),
  KEY `idx_approval_flow_scope` (`scope_type`, `scope_id`),
  KEY `idx_approval_flow_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批流配置表';

CREATE TABLE IF NOT EXISTS `approval_flow_step` (
  `step_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审批步骤ID',
  `flow_id` BIGINT UNSIGNED NOT NULL COMMENT '审批流ID',
  `step_order` INT NOT NULL COMMENT '步骤序号',
  `step_name` VARCHAR(100) NOT NULL COMMENT '步骤名称',
  `approver_type` VARCHAR(30) NOT NULL DEFAULT 'role' COMMENT '审批人类型：role/user/dept_manager',
  `role_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '审批角色ID',
  `user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '审批用户ID',
  `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '审批部门ID',
  `min_approvals` INT NOT NULL DEFAULT 1 COMMENT '最少通过人数',
  `allow_self_approve` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许自审',
  `data_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '步骤数据隔离：1全部 2本部门 3本部门及下级 4本人',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`step_id`),
  UNIQUE KEY `uk_approval_flow_step_order` (`flow_id`, `step_order`),
  KEY `idx_approval_flow_step_role` (`role_id`),
  KEY `idx_approval_flow_step_user` (`user_id`),
  KEY `idx_approval_flow_step_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批流步骤配置表';

INSERT IGNORE INTO `approval_flow`
(`feature_code`, `feature_name`, `node_code`, `node_name`, `scope_type`, `scope_id`, `data_scope`, `status`, `remark`)
VALUES
('pending-product-catalog', '待审批目录', 'initial-review', '目录初审', 'global', 'default', 1, 1, '待审批商品目录初审流程'),
('pending-product-catalog', '待审批目录', 'final-review', '目录终审', 'global', 'default', 1, 1, '待审批商品目录终审流程'),
('purchase-management', '采购管理', 'demand-review', '采购需求审核', 'global', 'default', 2, 1, '采购需求按部门数据范围审批'),
('purchase-management', '采购管理', 'plan-approval', '采购计划审批', 'global', 'default', 2, 1, '采购计划审批流程'),
('purchase-management', '采购管理', 'order-approval', '采购订单审批', 'global', 'default', 2, 1, '采购订单审批流程'),
('receiving-acceptance', '收货验收', 'receiving-approval', '收货验收审批', 'global', 'default', 2, 1, '收货异常及验收审批'),
('department-requisition', '科室申领', 'requisition-approval', '申领审批', 'global', 'default', 2, 1, '科室申领按科室隔离审批'),
('stocktaking-management', '盘点管理', 'stocktaking-approval', '盘点审批', 'global', 'default', 2, 1, '盘点结果审批流程'),
('batch-price-adjustment', '价格调整', 'price-adjustment-approval', '调价审批', 'global', 'default', 1, 1, '批量价格调整审批流程'),
('settlement-reconciliation', '结算对账', 'settlement-confirm', '结算确认', 'global', 'default', 1, 1, '结算确认审批流程');

INSERT IGNORE INTO `approval_flow_step`
(`flow_id`, `step_order`, `step_name`, `approver_type`, `role_id`, `min_approvals`, `allow_self_approve`, `data_scope`, `status`)
SELECT flow_id, 1, '一级审批', 'role',
       (SELECT role_id FROM sys_role WHERE role_code = 'approver' AND deleted = 0 LIMIT 1),
       1, 0, data_scope, 1
  FROM approval_flow
 WHERE deleted = 0;

INSERT IGNORE INTO `sys_permission` (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`)
SELECT p.perm_id, '审批流设置', 'approval-flow-settings', 1, '/features/approval-flow-settings', 3
  FROM sys_permission p
 WHERE p.perm_code = 'system-management'
 LIMIT 1;

INSERT IGNORE INTO `sys_permission` (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`)
SELECT p.perm_id, '保存审批流', 'approval-flow-settings:save', 2, NULL, 1
  FROM sys_permission p
 WHERE p.perm_code = 'approval-flow-settings'
 LIMIT 1;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id
  FROM sys_role r
  JOIN sys_permission p ON p.perm_code IN ('approval-flow-settings', 'approval-flow-settings:save')
 WHERE r.role_code = 'admin';
