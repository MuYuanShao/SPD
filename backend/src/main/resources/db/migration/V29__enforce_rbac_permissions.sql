-- Enforced RBAC catalog, custom department scopes, and retained verification fixtures.
CREATE TABLE IF NOT EXISTS `sys_role_dept` (
  `role_id` BIGINT UNSIGNED NOT NULL,
  `dept_id` BIGINT UNSIGNED NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`role_id`, `dept_id`),
  KEY `idx_role_dept_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色自定义数据权限部门';

INSERT IGNORE INTO `sys_permission` (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`) VALUES
(0,'工作台','dashboard',1,'/',1),
(0,'商品目录','product-catalog',1,'/features/product-catalog',10),
(0,'待审核目录','pending-product-catalog',1,'/features/pending-product-catalog',11),
(0,'医院目录','hospital-product-catalog',1,'/features/hospital-product-catalog',12),
(0,'供应商管理','supplier-management',1,'/features/supplier-management',13),
(0,'厂家管理','manufacturer-management',1,'/features/manufacturer-management',14),
(0,'院区管理','campus-management',1,'/features/campus-management',15),
(0,'科室管理','department-management',1,'/features/department-management',16),
(0,'科室库房目录','department-warehouse-catalog',1,'/features/department-warehouse-catalog',17),
(0,'库房货位管理','warehouse-location-management',1,'/features/warehouse-location-management',18),
(0,'定数包模板','quota-package-template',1,'/features/quota-package-template',20),
(0,'模板维护','quota-template-maintenance',1,'/features/quota-template-maintenance',21),
(0,'打包任务确认','packing-task-confirmation',1,'/features/packing-task-confirmation',22),
(0,'标签拆包','quota-label-unpack',1,'/features/quota-label-unpack',23),
(0,'定数包流水','quota-package-events',1,'/features/quota-package-events',24),
(0,'定数安全量','quota-safety-stock',1,'/features/quota-safety-stock',25),
(0,'采购管理','purchase-management',1,'/features/purchase-management',30),
(0,'收货验收','receiving-acceptance',1,'/features/receiving-acceptance',31),
(0,'库存管理','inventory-management',1,'/features/inventory-management',32),
(0,'库存交易流水','inventory-events',1,'/features/inventory-events',33),
(0,'补货任务','replenishment-task',1,'/features/replenishment-task',34),
(0,'拣配配送','picking-delivery',1,'/features/picking-delivery',35),
(0,'科室申领','department-requisition',1,'/features/department-requisition',36),
(0,'科室消耗','department-consumption',1,'/features/department-consumption',37),
(0,'盘点管理','stocktaking-management',1,'/features/stocktaking-management',38),
(0,'UDI追溯','udi-traceability',1,'/features/udi-traceability',40),
(0,'收费耗材明细','high-value-consumables',1,'/features/high-value-consumables',41),
(0,'冷链监控','cold-chain-monitoring',1,'/features/cold-chain-monitoring',42),
(0,'召回与隔离','recall-isolation',1,'/features/recall-isolation',43),
(0,'结算对账','settlement-reconciliation',1,'/features/settlement-reconciliation',50),
(0,'批次调价','batch-price-adjustment',1,'/features/batch-price-adjustment',51),
(0,'发票管理','invoice-management',1,'/features/invoice-management',52),
(0,'红冲管理','red-flush-management',1,'/features/red-flush-management',53),
(0,'运营驾驶舱','operation-cockpit',1,'/features/operation-cockpit',60),
(0,'报表中心','report-center',1,'/features/report-center',61),
(0,'决策大屏','decision-screen',1,'/features/decision-screen',62),
(0,'审批流设置','approval-flow-settings',1,'/features/approval-flow-settings',72),
(0,'系统配置','system-config',1,'/features/system-config',73),
(0,'配置命中说明','config-hit-explanation',1,'/features/config-hit-explanation',74),
(0,'审计日志','audit-log',1,'/features/audit-log',75),
(0,'缺货提醒','shortage-reminder',1,'/features/shortage-reminder',80),
(0,'拣配配送扩展','picking-distribution',1,'/features/picking-distribution',81),
(0,'反消耗','reverse-consumption',1,'/features/reverse-consumption',82),
(0,'供应商明细口径','supplier-detail-caliber',1,'/features/supplier-detail-caliber',83),
(0,'PDA离线记录','pda-offline-record',1,'/features/pda-offline-record',84),
(0,'业务闭环API','operational-closure',1,NULL,99);

INSERT IGNORE INTO `sys_permission` (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`)
SELECT 0, CONCAT(perm_name, '写操作'), CONCAT(perm_code, ':write'), 2, NULL, sort_order
  FROM `sys_permission`
 WHERE deleted = 0 AND perm_type = 1
   AND perm_code NOT IN ('master-data-quota','supply-chain','system-management');

-- Existing fine-grained administration button permissions remain authoritative.
INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'admin' AND r.deleted = 0 AND p.deleted = 0;

-- Sensible retained baseline for hospital operational roles.
INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('dashboard','hospital-product-catalog','hospital-product-catalog:write','supplier-management','supplier-management:write','manufacturer-management','manufacturer-management:write','pending-product-catalog','pending-product-catalog:write','purchase-management','purchase-management:write','receiving-acceptance','receiving-acceptance:write')
 WHERE r.role_code = 'purchaser' AND r.deleted = 0 AND p.deleted = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('dashboard','receiving-acceptance','receiving-acceptance:write','inventory-management','inventory-management:write','inventory-events','replenishment-task','replenishment-task:write','picking-delivery','picking-delivery:write','stocktaking-management','stocktaking-management:write','warehouse-location-management','warehouse-location-management:write','operational-closure','operational-closure:write')
 WHERE r.role_code = 'warehouse' AND r.deleted = 0 AND p.deleted = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('dashboard','department-requisition','department-requisition:write','department-consumption','department-consumption:write','pda-offline-record','pda-offline-record:write','operational-closure','operational-closure:write')
 WHERE r.role_code = 'dept_user' AND r.deleted = 0 AND p.deleted = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('dashboard','settlement-reconciliation','settlement-reconciliation:write','batch-price-adjustment','batch-price-adjustment:write','invoice-management','invoice-management:write','red-flush-management','red-flush-management:write','operational-closure','operational-closure:write')
 WHERE r.role_code = 'finance' AND r.deleted = 0 AND p.deleted = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('dashboard','udi-traceability','pending-product-catalog','pending-product-catalog:write','cold-chain-monitoring','cold-chain-monitoring:write','recall-isolation','recall-isolation:write','audit-log','operational-closure')
 WHERE r.role_code = 'qc' AND r.deleted = 0 AND p.deleted = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('dashboard','department-requisition','department-requisition:write','department-consumption','department-consumption:write','high-value-consumables','udi-traceability','operational-closure','operational-closure:write')
 WHERE r.role_code = 'doctor' AND r.deleted = 0 AND p.deleted = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('dashboard','operation-cockpit','report-center','decision-screen','inventory-management','inventory-events','purchase-management','settlement-reconciliation','invoice-management','operational-closure')
 WHERE r.role_code IN ('operator','leader','auditor') AND r.deleted = 0 AND p.deleted = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('dashboard','pending-product-catalog','pending-product-catalog:write','approval-flow-settings')
 WHERE r.role_code = 'approver' AND r.deleted = 0 AND p.deleted = 0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('dashboard','hospital-product-catalog','purchase-management','receiving-acceptance')
 WHERE r.role_code = 'supplier' AND r.deleted = 0 AND p.deleted = 0;

-- Retained verification fixtures. Password is the existing local default admin123 BCrypt hash.
INSERT IGNORE INTO `sys_role` (`role_name`,`role_code`,`description`,`data_scope`,`status`,`sort_order`) VALUES
('RBAC目录只读测试','rbac_catalog_reader','永久保留：验证仅医院目录菜单和只读API',4,1,900),
('RBAC本科室测试','rbac_department_reader','永久保留：验证本科室用户数据隔离',2,1,901);

INSERT IGNORE INTO `sys_role_perm` (`role_id`,`perm_id`)
SELECT r.role_id,p.perm_id FROM sys_role r JOIN sys_permission p ON p.perm_code IN ('dashboard','hospital-product-catalog')
 WHERE r.role_code='rbac_catalog_reader' AND r.deleted=0 AND p.deleted=0;

INSERT IGNORE INTO `sys_role_perm` (`role_id`,`perm_id`)
SELECT r.role_id,p.perm_id FROM sys_role r JOIN sys_permission p ON p.perm_code IN ('dashboard','user-management')
 WHERE r.role_code='rbac_department_reader' AND r.deleted=0 AND p.deleted=0;

INSERT IGNORE INTO `sys_user` (`username`,`password`,`real_name`,`phone`,`email`,`gender`,`dept_id`,`status`)
SELECT 'rbac_catalog_reader','$2b$10$sqUFZRSh5HGB2.LimvxVYenoSep050c5t1C7kg4.JIxICuUuUUmAS','RBAC目录测试','13900009001','rbac.catalog@hospital.local',0,MIN(dept_id),1 FROM sys_dept WHERE deleted=0;

INSERT IGNORE INTO `sys_user` (`username`,`password`,`real_name`,`phone`,`email`,`gender`,`dept_id`,`status`)
SELECT 'rbac_department_reader','$2b$10$sqUFZRSh5HGB2.LimvxVYenoSep050c5t1C7kg4.JIxICuUuUUmAS','RBAC科室测试','13900009002','rbac.department@hospital.local',0,MIN(dept_id),1 FROM sys_dept WHERE deleted=0;

INSERT IGNORE INTO `sys_user_role` (`user_id`,`role_id`)
SELECT u.user_id,r.role_id FROM sys_user u JOIN sys_role r
 WHERE (u.username='rbac_catalog_reader' AND r.role_code='rbac_catalog_reader')
    OR (u.username='rbac_department_reader' AND r.role_code='rbac_department_reader');
