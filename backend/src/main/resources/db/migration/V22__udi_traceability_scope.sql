ALTER TABLE `udi_trace_code`
  ADD COLUMN `trace_scope` VARCHAR(40) NOT NULL DEFAULT 'high_value' COMMENT 'Trace scope: high_value/low_value_quota_pack' AFTER `unique_code`,
  ADD COLUMN `package_label_no` VARCHAR(80) DEFAULT NULL COMMENT 'Quota package label no' AFTER `trace_scope`,
  ADD COLUMN `template_code` VARCHAR(80) DEFAULT NULL COMMENT 'Quota package template code' AFTER `package_label_no`,
  ADD COLUMN `template_name` VARCHAR(120) DEFAULT NULL COMMENT 'Quota package template name' AFTER `template_code`,
  ADD COLUMN `package_quantity` DECIMAL(18,4) DEFAULT NULL COMMENT 'Quantity in package' AFTER `template_name`,
  ADD COLUMN `package_unit` VARCHAR(30) DEFAULT NULL COMMENT 'Package unit' AFTER `package_quantity`,
  ADD COLUMN `package_status` VARCHAR(40) DEFAULT NULL COMMENT 'Quota package status' AFTER `package_unit`,
  ADD KEY `idx_udi_trace_scope` (`trace_scope`),
  ADD KEY `idx_udi_package_label` (`package_label_no`);

UPDATE `udi_trace_code`
   SET `trace_scope` = 'high_value'
 WHERE `trace_scope` = 'high_value';

INSERT INTO `udi_trace_code` (
  `udi_code`, `unique_code`, `trace_scope`, `package_label_no`, `template_code`, `template_name`,
  `package_quantity`, `package_unit`, `package_status`, `product_code`, `product_name`, `spec_model`,
  `manufacturer_name`, `supplier_name`, `batch_no`, `expire_date`, `current_location`, `current_department`,
  `current_status`, `responsible_person`, `patient_no`, `patient_name_masked`, `risk_level`, `last_event_name`, `last_event_time`
) VALUES
('069987654321000001', 'HV202607070001', 'high_value', NULL, NULL, NULL, NULL, NULL, NULL,
 'HV-STENT-001', '冠脉药物洗脱支架', '3.0mm x 18mm', '上海介入医疗科技有限公司', '高值耗材配送中心',
 'HVLOT20260707001', '2029-07-31', '导管室二级库', '心内科', 'consumed', '刘护士',
 'ZY-202607070088', '陈**', 'normal', '高值耗材计费', '2026-07-07 11:35:00'),
('QP-LOW-202607070001', 'QPL202607070001', 'low_value_quota_pack', 'QPL202607070001', 'TPL-GAUZE-001', '手术室纱布定数包',
 20.0000, '片', 'delivered', 'PROD-003', '医用脱脂纱布块', '8cm x 10cm', '南京康护医疗用品有限公司',
 '南京康护供应链', 'GAUZE20260707001', '2028-03-31', '手术室二级库', '手术室', 'signed', '赵护士',
 NULL, NULL, 'normal', '定数包科室签收', '2026-07-07 10:25:00');

INSERT INTO `udi_trace_event` (
  `trace_code_id`, `event_no`, `event_type`, `event_name`, `biz_no`, `location_name`, `department_name`,
  `operator_name`, `event_time`, `status`, `remark`, `sort_order`
)
SELECT `trace_code_id`, 'UT202607070001', 'supplier_ship', '供应商发货', 'PO202607070011', '高值耗材配送中心', NULL, '供应商高值专员', '2026-07-07 08:10:00', 'done', '单件 UDI 随货绑定', 10
  FROM `udi_trace_code` WHERE `unique_code` = 'HV202607070001'
UNION ALL
SELECT `trace_code_id`, 'UT202607070002', 'center_inbound', '中心库入库', 'RK202607070020', 'SPD中心库高值区', NULL, '高值库管员', '2026-07-07 09:05:00', 'done', '高值验收合格', 20
  FROM `udi_trace_code` WHERE `unique_code` = 'HV202607070001'
UNION ALL
SELECT `trace_code_id`, 'UT202607070003', 'department_requisition', '科室申领', 'SL202607070026', '导管室二级库', '心内科', '导管室护士', '2026-07-07 10:15:00', 'done', '手术备货申领', 30
  FROM `udi_trace_code` WHERE `unique_code` = 'HV202607070001'
UNION ALL
SELECT `trace_code_id`, 'UT202607070004', 'department_consumption', '术中使用', 'XH202607070041', '导管室 2 号间', '心内科', '刘护士', '2026-07-07 11:20:00', 'done', '绑定住院患者使用', 40
  FROM `udi_trace_code` WHERE `unique_code` = 'HV202607070001'
UNION ALL
SELECT `trace_code_id`, 'UT202607070005', 'high_value_charge', '高值耗材计费', 'JF202607070015', '导管室 2 号间', '心内科', '收费员', '2026-07-07 11:35:00', 'done', '生成患者计费明细', 50
  FROM `udi_trace_code` WHERE `unique_code` = 'HV202607070001'
UNION ALL
SELECT `trace_code_id`, 'UT202607070006', 'quota_pack_task', '定数包打包任务', 'DB202607070006', 'SPD中心库打包台', NULL, '打包员', '2026-07-07 08:30:00', 'done', '按模板生成低值定数包', 10
  FROM `udi_trace_code` WHERE `unique_code` = 'QPL202607070001'
UNION ALL
SELECT `trace_code_id`, 'UT202607070007', 'quota_pack_print', '定数包标签打印', 'QPL202607070001', 'SPD中心库打包台', NULL, '打包员', '2026-07-07 08:45:00', 'done', '打印包码并绑定来源批次', 20
  FROM `udi_trace_code` WHERE `unique_code` = 'QPL202607070001'
UNION ALL
SELECT `trace_code_id`, 'UT202607070008', 'picking_delivery', '拣货配送', 'PS202607070018', '配送暂存区', '手术室', '配送员', '2026-07-07 09:40:00', 'done', '按包码拣配配送', 30
  FROM `udi_trace_code` WHERE `unique_code` = 'QPL202607070001'
UNION ALL
SELECT `trace_code_id`, 'UT202607070009', 'department_sign', '定数包科室签收', 'QS202607070018', '手术室二级库', '手术室', '赵护士', '2026-07-07 10:25:00', 'done', '科室按包签收', 40
  FROM `udi_trace_code` WHERE `unique_code` = 'QPL202607070001';
