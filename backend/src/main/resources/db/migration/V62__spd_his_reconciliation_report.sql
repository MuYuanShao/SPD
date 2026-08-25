ALTER TABLE `product`
  ADD COLUMN `medical_insurance_code` VARCHAR(80) DEFAULT NULL COMMENT '国家医保耗材编码' AFTER `udi_code`,
  ADD KEY `idx_product_medical_insurance_code` (`medical_insurance_code`);

CREATE TABLE IF NOT EXISTS `his_charge_detail` (
  `charge_detail_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'HIS收费明细ID',
  `external_charge_no` VARCHAR(100) NOT NULL COMMENT 'HIS收费明细号',
  `source_system` VARCHAR(40) NOT NULL DEFAULT 'HIS' COMMENT '来源系统',
  `dept_name` VARCHAR(80) NOT NULL COMMENT '收费科室',
  `patient_no` VARCHAR(50) NOT NULL COMMENT '住院号/患者编号',
  `patient_name_masked` VARCHAR(80) DEFAULT NULL COMMENT '患者脱敏姓名',
  `product_code` VARCHAR(50) NOT NULL COMMENT 'SPD耗材编码',
  `medical_insurance_code` VARCHAR(80) DEFAULT NULL COMMENT 'HIS回传医保编码',
  `product_name` VARCHAR(120) NOT NULL COMMENT 'HIS回传耗材名称',
  `quantity` DECIMAL(18,4) NOT NULL COMMENT '计费数量',
  `unit_price` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '收费单价',
  `amount` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '收费金额',
  `charge_time` DATETIME NOT NULL COMMENT '收费时间',
  `status` VARCHAR(30) NOT NULL DEFAULT 'charged' COMMENT '收费状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  PRIMARY KEY (`charge_detail_id`),
  UNIQUE KEY `uk_his_charge_detail_external_no` (`external_charge_no`),
  KEY `idx_his_charge_detail_reconcile` (`charge_time`, `dept_name`, `patient_no`, `product_code`),
  KEY `idx_his_charge_detail_insurance_code` (`medical_insurance_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HIS耗材收费明细回传表';

INSERT IGNORE INTO `sys_permission`
  (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `component`, `icon`, `sort_order`, `status`)
SELECT COALESCE(parent_perm.perm_id, 0), 'SPD-HIS收费核对', 'spd-his-reconciliation-report', 1,
       '/features/spd-his-reconciliation-report', 'operations/SpdHisReconciliationReportView',
       'clipboard-check', 611, 1
  FROM (SELECT 1) seed
  LEFT JOIN `sys_permission` parent_perm ON parent_perm.perm_code = 'report-center'
 LIMIT 1;

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT role_row.role_id, permission_row.perm_id
  FROM `sys_role` role_row
  JOIN `sys_permission` permission_row ON permission_row.perm_code = 'spd-his-reconciliation-report'
 WHERE role_row.role_code IN ('admin', 'operator', 'leader', 'auditor')
   AND role_row.deleted = 0
   AND permission_row.deleted = 0;
