USE `ISPD`;

INSERT IGNORE INTO `pending_product_application`
(`application_no`, `application_type`, `supplier_id`, `product_name`, `product_code`, `spec_model`,
 `manufacturer_name`, `unit`, `purchase_price`, `retail_price`, `min_purchase_qty`, `purchase_unit`,
 `conversion_rate`, `registration_no`, `registration_expire_date`, `qualification_attachment_count`,
 `is_high_value`, `is_cold_chain`, `is_quota_managed`, `storage_condition`, `product_snapshot`,
 `approval_status`, `submit_by`, `submit_time`)
VALUES
('SP20260427001', '新品准入', NULL, '一次性使用无菌注射器 10ml', 'P-NEW-001', '10ml',
 '国药器械', '支', 1.2000, NULL, 1, '盒', 100.000000, '械注准20260001', '2029-12-31', 2,
 0, 0, 1, '常温', JSON_OBJECT('source', 'seed'), 'pending_initial', 1, '2026-05-25 09:30:00'),
('SP20260427005', '新品准入', NULL, '一次性使用采血针', 'P-NEW-002', '0.7mm',
 '康德莱器械', '支', 0.8500, NULL, 1, '盒', 100.000000, '械注准20260002', '2029-11-30', 2,
 0, 0, 1, '常温', JSON_OBJECT('source', 'seed'), 'pending_initial', 7, '2026-05-25 11:05:00'),
('SP20260427004', '信息变更', NULL, '葡萄糖测定试剂盒', 'P-CHG-001', '50T',
 '迈瑞生物', '盒', 88.0000, NULL, 1, '盒', 1.000000, '械注准20250004', '2028-08-31', 1,
 0, 1, 0, '冷藏', JSON_OBJECT('changedFields', JSON_ARRAY('规格型号', '储存条件')), 'rejected', 6, '2026-05-24 14:20:00'),
('SP20260427003', '资质更新', NULL, '医用脱脂纱布块', 'P-QUA-001', '8cm*10cm',
 '稳健医疗', '包', 12.0000, NULL, 1, '包', 1.000000, '械注准20240003', '2030-06-30', 3,
 0, 0, 1, '常温', JSON_OBJECT('qualification', 'registration'), 'pending_final', 5, '2026-05-24 16:40:00'),
('SP20260427002', '价格调整', NULL, '静脉留置针 22G', 'P-PRI-001', '22G',
 '九州通', '支', 15.0000, NULL, 1, '盒', 50.000000, '械注准20250002', '2028-10-31', 1,
 0, 0, 1, '常温', JSON_OBJECT('oldPrice', 12, 'newPrice', 15), 'pending_initial', 4, '2026-05-25 10:15:00');
