-- Compensate schema drift without rewriting historical checksums or changing role grants.
CREATE TABLE IF NOT EXISTS supplier_invoice (
  invoice_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  invoice_no VARCHAR(40) NOT NULL COMMENT 'SPD invoice document number',
  supplier_id BIGINT UNSIGNED NOT NULL,
  invoice_code VARCHAR(40) NOT NULL,
  invoice_number VARCHAR(80) NOT NULL,
  invoice_date DATE NOT NULL,
  amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  settlement_id BIGINT UNSIGNED NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'pending_verification',
  remark VARCHAR(500) NULL,
  verify_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (invoice_id),
  UNIQUE KEY uk_supplier_invoice_no (invoice_no),
  UNIQUE KEY uk_supplier_invoice_identity (supplier_id, invoice_code, invoice_number),
  KEY idx_supplier_invoice_date (invoice_date),
  KEY idx_supplier_invoice_status (status),
  KEY idx_supplier_invoice_settlement (settlement_id),
  CONSTRAINT fk_supplier_invoice_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (supplier_id),
  CONSTRAINT fk_supplier_invoice_settlement FOREIGN KEY (settlement_id) REFERENCES settlement_bill (settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Supplier invoice registration and verification';

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

