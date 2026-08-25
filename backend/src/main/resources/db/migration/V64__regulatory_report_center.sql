CREATE TABLE IF NOT EXISTS `centralized_procurement_task` (
  `task_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '集采任务ID',
  `batch_code` VARCHAR(80) NOT NULL COMMENT '集采批次编码',
  `batch_name` VARCHAR(120) NOT NULL COMMENT '集采批次名称',
  `task_year` INT NOT NULL COMMENT '任务年度',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `selected_manufacturer_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '中选厂家ID',
  `selected_price` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '中选价格',
  `annual_target_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '年度任务量',
  `incomplete_reason` VARCHAR(500) DEFAULT NULL COMMENT '未完成原因',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_centralized_task_batch_product` (`batch_code`, `product_id`),
  KEY `idx_centralized_task_year` (`task_year`),
  KEY `idx_centralized_task_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='集采年度执行任务表';

INSERT IGNORE INTO `sys_permission`
  (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `component`, `icon`, `sort_order`, `status`)
SELECT COALESCE(parent_perm.perm_id, 0), permission_seed.perm_name, permission_seed.perm_code, 1,
       permission_seed.path, permission_seed.component, permission_seed.icon, permission_seed.sort_order, 1
  FROM (
    SELECT '供应商供货明细台账' AS perm_name, 'supplier-delivery-ledger-report' AS perm_code,
           '/features/supplier-delivery-ledger-report' AS path,
           'operations/SupplierDeliveryLedgerReportView' AS component, 'truck' AS icon, 612 AS sort_order
    UNION ALL
    SELECT '集采执行进度报表', 'centralized-procurement-progress-report',
           '/features/centralized-procurement-progress-report',
           'operations/CentralizedProcurementProgressReportView', 'chart-no-axes-combined', 613
    UNION ALL
    SELECT '全院物资进销存汇总表', 'inventory-movement-summary-report',
           '/features/inventory-movement-summary-report',
           'operations/InventoryMovementSummaryReportView', 'warehouse', 614
  ) permission_seed
  LEFT JOIN `sys_permission` parent_perm ON parent_perm.perm_code = 'report-center';

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT role_row.role_id, permission_row.perm_id
  FROM `sys_role` role_row
  JOIN `sys_permission` permission_row
    ON permission_row.perm_code IN (
      'supplier-delivery-ledger-report',
      'centralized-procurement-progress-report',
      'inventory-movement-summary-report'
    )
 WHERE role_row.role_code IN ('admin', 'operator', 'leader', 'auditor')
   AND role_row.deleted = 0
   AND permission_row.deleted = 0;
