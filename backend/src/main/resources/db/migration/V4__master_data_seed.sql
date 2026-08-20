-- Master data seed: roles, suppliers, manufacturers, departments, product categories, products, warehouses, locations

INSERT IGNORE INTO `sys_role` (`role_name`, `role_code`, `description`, `data_scope`, `status`, `sort_order`) VALUES
('系统管理员', 'admin', '系统超级管理员，拥有所有权限', 1, 1, 1),
('运营管理员', 'operator', '日常运营管理，库存、采购等操作', 3, 1, 2),
('采购人员', 'purchaser', '采购计划、订单管理、供应商沟通', 2, 1, 3),
('库管人员', 'warehouse', '入库、出库、盘点、库存管理', 2, 1, 4),
('科室人员', 'dept_user', '科室申领、使用登记', 4, 1, 5),
('财务人员', 'finance', '结算对账、发票管理、付款审批', 2, 1, 6),
('质控人员', 'qc', '质量监控、追溯审计、合规检查', 1, 1, 7),
('医生/护士', 'doctor', '高值耗材使用、患者关联', 4, 1, 8),
('院领导', 'leader', '运营总览、决策分析', 1, 1, 9),
('审计人员', 'auditor', '数据查询、报表查看，无操作权限', 1, 1, 10),
('供应商', 'supplier', '外部供应商，查看订单和配送', 4, 1, 11),
('审批人员', 'approver', '各业务审批节点处理', 3, 1, 12);

INSERT IGNORE INTO `supplier`
(`supplier_code`, `supplier_name`, `credit_code`, `supplier_type`, `grade`, `contact_name`, `contact_phone`, `email`, `address`, `approval_status`, `status`)
VALUES
('SUP-001', '国药器械', '913100001000000001', '配送商', 'A', '张经理', '13800000001', 'sup001@example.com', '上海市浦东新区', 'approved', 1),
('SUP-002', '九州通', '914200001000000002', '经销商', 'B', '李经理', '13800000002', 'sup002@example.com', '武汉市汉阳区', 'approved', 1),
('SUP-003', '稳健医疗', '914403001000000003', '生产商', 'A', '王经理', '13800000003', 'sup003@example.com', '深圳市龙华区', 'approved', 1);

INSERT IGNORE INTO `manufacturer`
(`manufacturer_code`, `manufacturer_name`, `credit_code`, `license_no`, `contact_name`, `contact_phone`, `address`, `status`)
VALUES
('MFR-001', '国药器械', '913100001000000001', '沪食药监械生产许20260001号', '张经理', '13800000001', '上海市浦东新区', 1),
('MFR-002', '迈瑞生物', '914403001000000004', '粤食药监械生产许20260002号', '赵经理', '13800000004', '深圳市南山区', 1),
('MFR-003', '康德莱器械', '913100001000000005', '沪食药监械生产许20260003号', '孙经理', '13800000005', '上海市嘉定区', 1);

INSERT IGNORE INTO `sys_dept`
(`dept_code`, `dept_name`, `campus_name`, `finance_dept_code`, `finance_dept_name`, `manager_name`, `phone`, `sort_order`, `status`)
VALUES
('DEPT-ORTH', '骨科', '主院区', 'FIN-ORTH', '骨科成本中心', '陈主任', '021-10000001', 1, 1),
('DEPT-CARD', '心内科', '主院区', 'FIN-CARD', '心内科成本中心', '周主任', '021-10000002', 2, 1),
('DEPT-OR', '手术室', '主院区', 'FIN-OR', '手术室成本中心', '吴护士长', '021-10000003', 3, 1);

INSERT IGNORE INTO `product_category`
(`category_code`, `category_name`, `parent_id`, `level`, `sort_order`, `status`)
VALUES
('CAT-001', '注射器类', 0, 1, 1, 1),
('CAT-002', '留置针类', 0, 1, 2, 1),
('CAT-003', '敷料类', 0, 1, 3, 1);

INSERT IGNORE INTO `product`
(`product_code`, `product_name`, `spec_model`, `brand`, `manufacturer_id`, `supplier_id`, `category_id`, `unit`, `purchase_price`, `purchase_unit`, `conversion_rate`, `registration_no`, `registration_expire_date`, `is_high_value`, `is_cold_chain`, `is_quota_managed`, `storage_condition`, `status`)
SELECT 'PROD-001', '一次性使用无菌注射器 10ml', '10ml', '国药', m.manufacturer_id, s.supplier_id, c.category_id, '支', 1.2000, '盒', 100, '械注准20260001', '2029-12-31', 0, 0, 1, '常温', 1
FROM manufacturer m, supplier s, product_category c
WHERE m.manufacturer_code='MFR-001' AND s.supplier_code='SUP-001' AND c.category_code='CAT-001';

INSERT IGNORE INTO `product`
(`product_code`, `product_name`, `spec_model`, `brand`, `manufacturer_id`, `supplier_id`, `category_id`, `unit`, `purchase_price`, `purchase_unit`, `conversion_rate`, `registration_no`, `registration_expire_date`, `is_high_value`, `is_cold_chain`, `is_quota_managed`, `storage_condition`, `status`)
SELECT 'PROD-002', '静脉留置针 22G', '22G', '九州通', m.manufacturer_id, s.supplier_id, c.category_id, '支', 15.0000, '盒', 50, '械注准20250002', '2028-10-31', 0, 0, 1, '常温', 1
FROM manufacturer m, supplier s, product_category c
WHERE m.manufacturer_code='MFR-003' AND s.supplier_code='SUP-002' AND c.category_code='CAT-002';

INSERT IGNORE INTO `product`
(`product_code`, `product_name`, `spec_model`, `brand`, `manufacturer_id`, `supplier_id`, `category_id`, `unit`, `purchase_price`, `purchase_unit`, `conversion_rate`, `registration_no`, `registration_expire_date`, `is_high_value`, `is_cold_chain`, `is_quota_managed`, `storage_condition`, `status`)
SELECT 'PROD-003', '医用脱脂纱布块', '8cm*10cm', '稳健', m.manufacturer_id, s.supplier_id, c.category_id, '包', 12.0000, '包', 1, '械注准20240003', '2030-06-30', 0, 0, 1, '常温', 1
FROM manufacturer m, supplier s, product_category c
WHERE m.manufacturer_code='MFR-002' AND s.supplier_code='SUP-003' AND c.category_code='CAT-003';

INSERT IGNORE INTO `warehouse`
(`warehouse_code`, `warehouse_name`, `warehouse_type`, `campus_name`, `dept_id`, `participate_stats`, `status`)
VALUES
('WH-CENTER', 'SPD中心库', '中心库', '主院区', NULL, 1, 1);

INSERT IGNORE INTO `warehouse`
(`warehouse_code`, `warehouse_name`, `warehouse_type`, `campus_name`, `dept_id`, `participate_stats`, `status`)
SELECT 'WH-ORTH', '骨科二级库', '科室二级库', '主院区', dept_id, 1, 1 FROM sys_dept WHERE dept_code='DEPT-ORTH';

INSERT IGNORE INTO `warehouse`
(`warehouse_code`, `warehouse_name`, `warehouse_type`, `campus_name`, `dept_id`, `participate_stats`, `status`)
SELECT 'WH-COLD', '冷链库', '冷库', '主院区', dept_id, 1, 1 FROM sys_dept WHERE dept_code='DEPT-OR';

INSERT IGNORE INTO `warehouse_location`
(`warehouse_id`, `location_code`, `location_type`, `capacity_limit`, `status`)
SELECT warehouse_id, 'A-01-01-01', '整件位', 1000, 1 FROM warehouse WHERE warehouse_code='WH-CENTER';

INSERT IGNORE INTO `warehouse_location`
(`warehouse_id`, `location_code`, `location_type`, `capacity_limit`, `status`)
SELECT warehouse_id, 'B-01-01-01', '散货位', 300, 1 FROM warehouse WHERE warehouse_code='WH-ORTH';
