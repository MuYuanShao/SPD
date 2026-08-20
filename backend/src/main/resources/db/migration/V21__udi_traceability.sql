CREATE TABLE IF NOT EXISTS `udi_trace_code` (
  `trace_code_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'UDI trace code ID',
  `udi_code` VARCHAR(120) NOT NULL COMMENT 'UDI code',
  `unique_code` VARCHAR(120) NOT NULL COMMENT 'Unique trace code',
  `product_code` VARCHAR(64) NOT NULL COMMENT 'Product code',
  `product_name` VARCHAR(255) NOT NULL COMMENT 'Product name',
  `spec_model` VARCHAR(255) DEFAULT NULL COMMENT 'Specification',
  `manufacturer_name` VARCHAR(255) DEFAULT NULL COMMENT 'Manufacturer',
  `supplier_name` VARCHAR(255) DEFAULT NULL COMMENT 'Supplier',
  `batch_no` VARCHAR(100) DEFAULT NULL COMMENT 'Batch no',
  `expire_date` DATE DEFAULT NULL COMMENT 'Expire date',
  `current_location` VARCHAR(255) DEFAULT NULL COMMENT 'Current location',
  `current_department` VARCHAR(255) DEFAULT NULL COMMENT 'Current department',
  `current_status` VARCHAR(40) NOT NULL DEFAULT 'in_stock' COMMENT 'Current status',
  `responsible_person` VARCHAR(80) DEFAULT NULL COMMENT 'Responsible person',
  `patient_no` VARCHAR(80) DEFAULT NULL COMMENT 'Patient no',
  `patient_name_masked` VARCHAR(80) DEFAULT NULL COMMENT 'Masked patient name',
  `risk_level` VARCHAR(40) NOT NULL DEFAULT 'normal' COMMENT 'Risk level',
  `last_event_name` VARCHAR(100) DEFAULT NULL COMMENT 'Last event name',
  `last_event_time` DATETIME DEFAULT NULL COMMENT 'Last event time',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`trace_code_id`),
  UNIQUE KEY `uk_udi_trace_code` (`udi_code`),
  UNIQUE KEY `uk_udi_unique_code` (`unique_code`),
  KEY `idx_udi_product` (`product_code`),
  KEY `idx_udi_batch` (`batch_no`),
  KEY `idx_udi_status` (`current_status`),
  KEY `idx_udi_department` (`current_department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UDI and unique-code trace master';

CREATE TABLE IF NOT EXISTS `udi_trace_event` (
  `trace_event_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'UDI trace event ID',
  `trace_code_id` BIGINT UNSIGNED NOT NULL COMMENT 'Trace code ID',
  `event_no` VARCHAR(80) NOT NULL COMMENT 'Event no',
  `event_type` VARCHAR(64) NOT NULL COMMENT 'Event type',
  `event_name` VARCHAR(100) NOT NULL COMMENT 'Event name',
  `biz_no` VARCHAR(100) DEFAULT NULL COMMENT 'Business no',
  `location_name` VARCHAR(255) DEFAULT NULL COMMENT 'Location',
  `department_name` VARCHAR(255) DEFAULT NULL COMMENT 'Department',
  `operator_name` VARCHAR(80) DEFAULT NULL COMMENT 'Operator',
  `event_time` DATETIME NOT NULL COMMENT 'Event time',
  `status` VARCHAR(40) NOT NULL DEFAULT 'done' COMMENT 'Event status',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT 'Sort order',
  PRIMARY KEY (`trace_event_id`),
  UNIQUE KEY `uk_udi_event_no` (`event_no`),
  KEY `idx_udi_event_code_time` (`trace_code_id`, `event_time`),
  KEY `idx_udi_event_type` (`event_type`),
  KEY `idx_udi_event_biz_no` (`biz_no`),
  CONSTRAINT `fk_udi_event_trace_code` FOREIGN KEY (`trace_code_id`) REFERENCES `udi_trace_code` (`trace_code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UDI and unique-code trace events';

INSERT INTO `udi_trace_code` (
  `udi_code`, `unique_code`, `product_code`, `product_name`, `spec_model`, `manufacturer_name`, `supplier_name`,
  `batch_no`, `expire_date`, `current_location`, `current_department`, `current_status`, `responsible_person`,
  `patient_no`, `patient_name_masked`, `risk_level`, `last_event_name`, `last_event_time`
) VALUES
('069123456789012345', 'UDI20260706000158', 'P-NEW-002', '一次性使用采血针', '0.7mm x 25mm', '苏州康采医疗器械有限公司', '华东医用耗材供应链', 'BATCH20260702150958', '2028-06-30', '检验科二级库', '检验科', 'consumed', '王护士', 'MZ-202607060118', '李**', 'normal', '科室消耗', '2026-07-06 10:45:00'),
('069123456789012346', 'UDI20260706000159', 'HC090281', '测试商品', '10片/包', '浙江医材科技有限公司', '华东医用耗材供应链', 'CODEVERIFY202607021509', '2027-12-31', 'SPD中心库 A-03-02', NULL, 'in_stock', '中心库管理员', NULL, NULL, 'normal', '中心库入库', '2026-07-06 09:15:00'),
('069123456789012347', 'UDI20260706000160', 'PROD-003', '医用脱脂纱布块', '8cm x 10cm', '南京康护医疗用品有限公司', '南京康护供应链', 'GAUZE20260702001', '2028-03-31', '手术室二级库', '手术室', 'signed', '赵护士', NULL, NULL, 'warning', '科室签收', '2026-07-06 11:20:00'),
('069123456789012348', 'UDI20260706000161', 'CH00001', '测试耗材', '1支/袋', '上海医械制造有限公司', '上海康桥供应链', 'RECALL20260701001', '2027-08-31', '隔离区 I-01', '供应链部', 'isolated', '质量管理员', NULL, NULL, 'exception', '召回隔离', '2026-07-06 13:05:00');

INSERT INTO `udi_trace_event` (
  `trace_code_id`, `event_no`, `event_type`, `event_name`, `biz_no`, `location_name`, `department_name`,
  `operator_name`, `event_time`, `status`, `remark`, `sort_order`
)
SELECT `trace_code_id`, 'UT202607060001', 'supplier_ship', '供应商发货', 'PO202607020001', '供应商仓', NULL, '供应商李工', '2026-07-02 09:20:00', 'done', '绑定 UDI 与生产批号', 10
  FROM `udi_trace_code` WHERE `unique_code` = 'UDI20260706000158'
UNION ALL
SELECT `trace_code_id`, 'UT202607060002', 'center_inbound', '中心库入库', 'RK202607020011', 'SPD中心库', NULL, '中心库管理员', '2026-07-02 15:09:00', 'done', '验收合格入库', 20
  FROM `udi_trace_code` WHERE `unique_code` = 'UDI20260706000158'
UNION ALL
SELECT `trace_code_id`, 'UT202607060003', 'shelf_putaway', '上架定位', 'SJ202607020032', 'SPD中心库 A-03-02', NULL, '库管员周工', '2026-07-02 16:10:00', 'done', '货位 A-03-02', 30
  FROM `udi_trace_code` WHERE `unique_code` = 'UDI20260706000158'
UNION ALL
SELECT `trace_code_id`, 'UT202607060004', 'department_requisition', '科室申领', 'SL202607050018', '检验科二级库', '检验科', '检验科张老师', '2026-07-05 08:40:00', 'done', '申领用于门诊采血', 40
  FROM `udi_trace_code` WHERE `unique_code` = 'UDI20260706000158'
UNION ALL
SELECT `trace_code_id`, 'UT202607060005', 'picking_delivery', '拣货配送', 'PS202607050029', '配送暂存区', '检验科', '配送员陈工', '2026-07-05 14:30:00', 'done', '随配送单出库', 50
  FROM `udi_trace_code` WHERE `unique_code` = 'UDI20260706000158'
UNION ALL
SELECT `trace_code_id`, 'UT202607060006', 'department_sign', '科室签收', 'QS202607050029', '检验科二级库', '检验科', '王护士', '2026-07-05 15:05:00', 'done', '二级库扫码签收', 60
  FROM `udi_trace_code` WHERE `unique_code` = 'UDI20260706000158'
UNION ALL
SELECT `trace_code_id`, 'UT202607060007', 'department_consumption', '科室消耗', 'XH202607060033', '门诊采血室', '检验科', '王护士', '2026-07-06 10:45:00', 'done', '门诊患者使用', 70
  FROM `udi_trace_code` WHERE `unique_code` = 'UDI20260706000158'
UNION ALL
SELECT `trace_code_id`, 'UT202607060008', 'center_inbound', '中心库入库', 'RK202607060015', 'SPD中心库 A-03-02', NULL, '中心库管理员', '2026-07-06 09:15:00', 'done', '待科室申领', 10
  FROM `udi_trace_code` WHERE `unique_code` = 'UDI20260706000159'
UNION ALL
SELECT `trace_code_id`, 'UT202607060009', 'department_sign', '科室签收', 'QS202607060020', '手术室二级库', '手术室', '赵护士', '2026-07-06 11:20:00', 'done', '签收待使用', 10
  FROM `udi_trace_code` WHERE `unique_code` = 'UDI20260706000160'
UNION ALL
SELECT `trace_code_id`, 'UT202607060010', 'recall_isolation', '召回隔离', 'ZH202607060004', '隔离区 I-01', '供应链部', '质量管理员', '2026-07-06 13:05:00', 'exception', '批次召回，禁止发放', 10
  FROM `udi_trace_code` WHERE `unique_code` = 'UDI20260706000161';
