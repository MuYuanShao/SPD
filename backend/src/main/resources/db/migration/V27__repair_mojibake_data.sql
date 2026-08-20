CREATE TABLE IF NOT EXISTS `data_quality_issue` (
  `issue_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据质量问题ID',
  `source_table` VARCHAR(80) NOT NULL COMMENT '来源表',
  `source_key` VARCHAR(120) NOT NULL COMMENT '稳定业务键',
  `issue_type` VARCHAR(50) NOT NULL COMMENT '问题类型',
  `original_data` JSON NOT NULL COMMENT '修复前快照',
  `resolution_status` VARCHAR(30) NOT NULL DEFAULT 'resolved' COMMENT 'resolved/reimport_required',
  `resolution_note` VARCHAR(500) NOT NULL COMMENT '处理说明',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`issue_id`),
  UNIQUE KEY `uk_data_quality_source` (`source_table`, `source_key`, `issue_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据质量修复留痕';

-- 这两批导入数据的中文内容已经被替换字符破坏，无法可靠反向恢复。
-- 先保存完整快照，再阻止其继续进入审批，并保留商品编码供重新导入时核对。
INSERT IGNORE INTO `data_quality_issue`
(`source_table`, `source_key`, `issue_type`, `original_data`, `resolution_status`, `resolution_note`)
SELECT 'pending_product_application', application_no, 'mojibake',
       JSON_OBJECT(
         'applicationType', application_type, 'productCode', product_code, 'productName', product_name,
         'specModel', spec_model, 'manufacturerName', manufacturer_name, 'unit', unit,
         'purchaseUnit', purchase_unit, 'registrationNo', registration_no,
         'storageCondition', storage_condition, 'approvalStatus', approval_status
       ),
       'reimport_required', '原始中文已不可逆损坏；已停止审批，需使用 UTF-8 模板重新导入'
  FROM `pending_product_application`
 WHERE application_no BETWEEN 'SP20260612001' AND 'SP20260612040';

UPDATE `pending_product_application`
   SET application_type = '新品准入',
       product_name = CONCAT('待重新导入（', product_code, '）'),
       spec_model = '待确认',
       manufacturer_name = '待重新导入',
       unit = '待确认',
       purchase_unit = '待确认',
       registration_no = CONCAT('REIMPORT-', application_no),
       storage_condition = '待确认',
       approval_status = 'rejected',
       approve_opinion = '原始导入数据编码损坏，请使用 UTF-8 模板重新导入'
 WHERE application_no BETWEEN 'SP20260612001' AND 'SP20260612040';

UPDATE `pending_product_application`
   SET storage_condition = '常温'
 WHERE application_no = 'SP20260527001';

UPDATE `pending_product_application`
   SET application_type = '新品准入', storage_condition = '常温'
 WHERE application_no = 'SP20260527003';

UPDATE `pending_product_application`
   SET approve_opinion = '初审通过', final_review_opinion = '终审通过'
 WHERE application_no = 'SP20260612198' AND approval_status = 'approved';

-- 按稳定业务编码恢复种子数据和自动化测试数据的可读名称。
UPDATE `sys_user` SET real_name = '系统管理员' WHERE username = 'admin';
UPDATE `sys_user` SET real_name = 'Codex测试用户' WHERE username = 'codex_user_edit_102138024';
UPDATE `sys_role` SET role_name = 'Codex测试角色' WHERE role_code IN ('codex_test_093941', 'role_20260629094553792');
UPDATE `sys_dept` SET dept_name = 'Codex测试科室' WHERE dept_code IN ('DEPT-CODEX-102605484', 'DEPT-CODEX-103027964');
UPDATE `sys_dept` SET campus_name = '主院区' WHERE dept_code = 'DEPT-CODEX-103027964';
UPDATE `product_category` SET category_name = '测试分类' WHERE category_code = 'CAT-1779845288937';
UPDATE `product` SET spec_model = '测试规格' WHERE product_code = 'SMARTTEST-P003';
UPDATE `product` SET storage_condition = '常温' WHERE product_code = 'P-AUTO-FLOW-092808';

UPDATE `approval_flow`
   SET feature_name = '待审批目录', node_name = '目录初审', remark = '待审批商品目录初审流程'
 WHERE feature_code = 'pending-product-catalog' AND node_code = 'initial-review';
UPDATE `approval_flow`
   SET feature_name = '待审批目录', node_name = '目录终审', remark = '待审批商品目录终审流程'
 WHERE feature_code = 'pending-product-catalog' AND node_code = 'final-review';
UPDATE `approval_flow`
   SET feature_name = '价格调整', node_name = '调价审批', remark = '批量价格调整审批流程'
 WHERE feature_code = 'batch-price-adjustment' AND node_code = 'price-adjustment-approval';
UPDATE `approval_flow_step` s
  JOIN `approval_flow` f ON f.flow_id = s.flow_id
   SET s.step_name = '一级审批'
 WHERE f.feature_code = 'batch-price-adjustment' AND f.node_code = 'price-adjustment-approval' AND s.step_order = 1;

-- 历史审计和跟踪记录按操作类型恢复为明确、可追溯的业务描述。
UPDATE `audit_log` a
  JOIN `purchase_order` po ON po.purchase_order_id = a.biz_id
   SET a.remark = CASE
     WHEN a.operation_type = 'submit' THEN '采购订单提交审批'
     WHEN a.operation_type = 'approve' THEN '采购订单审批通过'
     WHEN a.operation_type = 'send' THEN '采购订单已发送供应商'
     WHEN a.operation_type = 'close' THEN '采购订单已关闭'
     ELSE '历史审计备注编码损坏，原文不可恢复'
   END
 WHERE a.biz_type = 'purchase_order' AND po.order_no IN ('CG20260526002', 'CG20260526003');

UPDATE `audit_log` a
  JOIN `receiving_order` ro ON ro.receiving_order_id = a.biz_id
   SET a.remark = CASE
     WHEN a.operation_type = 'update' THEN '修改待验收收货单'
     WHEN a.operation_type = 'approve' THEN '验收审核通过，按最新医院目录采购价生成系统批次'
     ELSE '历史审计备注编码损坏，原文不可恢复'
   END
 WHERE a.biz_type = 'receiving_order' AND ro.receiving_no = 'RK20260603026';

UPDATE `purchase_order_tracking` t
  JOIN `purchase_order` po ON po.purchase_order_id = t.purchase_order_id
   SET t.remark = CASE t.event_type
     WHEN 'submit' THEN '采购订单提交审批'
     WHEN 'approve' THEN '采购订单审批通过'
     WHEN 'send' THEN '采购订单已发送供应商'
     WHEN 'close' THEN '采购订单已关闭'
     ELSE '历史跟踪备注编码损坏，原文不可恢复'
   END
 WHERE po.order_no IN ('CG20260526002', 'CG20260526003');

UPDATE `purchase_order` SET close_reason = '历史关闭原因编码损坏，原文不可恢复' WHERE order_no = 'CG20260526002';
UPDATE `purchase_plan` SET remark = '历史计划备注编码损坏，原文不可恢复' WHERE plan_no IN ('JH202605260001', 'JH202605260002');
UPDATE `purchase_demand`
   SET demand_source = '历史导入', remark = '历史需求备注编码损坏，原文不可恢复'
 WHERE demand_no = 'XQ202605260002';
UPDATE `receiving_order`
   SET remark = '验收审核通过，按最新医院目录采购价生成系统批次'
 WHERE receiving_no = 'RK20260611002';

UPDATE `replenishment_smart_analysis` a
  JOIN `replenishment_smart_analysis_item` i ON i.analysis_id = a.analysis_id
   SET a.dept_name = '测试科室', a.warehouse_name = '测试库房'
 WHERE i.product_code LIKE 'SMARTTEST-%';
UPDATE `replenishment_smart_analysis_item` i
  LEFT JOIN `product` p ON p.product_code = i.product_code
   SET i.product_name = COALESCE(p.product_name, CONCAT('测试商品（', i.product_code, '）'))
 WHERE i.product_code LIKE 'SMARTTEST-%';
