-- =====================================================================
-- 科室库房目录：长名称测试数据
-- ---------------------------------------------------------------------
-- 目的：验证列表在「商品名称」「生产厂家」超过 20 个字符时的换行表现，
--      覆盖标准 / 大 / 特大三档字号与可调列宽场景。
-- 说明：仅插入测试用商品、厂家与目录条目，
--      通过固定 TEST_LONG_* 前缀标记，便于后续清理。
-- =====================================================================

-- 长名称厂家
-- 说明：credit_code 为 VARCHAR(18)，此处使用 18 位测试值
INSERT INTO `manufacturer` (`manufacturer_code`, `manufacturer_name`, `credit_code`, `status`, `deleted`)
VALUES
  ('TEST_LONG_MFR_001', '上海康德莱医疗器械股份有限公司第一分公司', '91310000TESTLNG001', 1, 0),
  ('TEST_LONG_MFR_002', '江苏华东医用高分子材料集团有限公司', '91320000TESTLNG002', 1, 0)
ON DUPLICATE KEY UPDATE `manufacturer_name` = VALUES(`manufacturer_name`), `deleted` = 0;

-- 长名称商品
-- 说明：product_id 使用 990001+ 区间，避开既有种子数据（900001+）与自增段
INSERT INTO `product` (
  `product_id`, `product_code`, `product_name`, `spec_model`, `brand`,
  `manufacturer_id`, `category_id`, `unit`, `purchase_price`, `min_purchase_qty`,
  `purchase_unit`, `conversion_rate`, `status`, `deleted`
)
SELECT
  p.product_id,
  p.product_code,
  p.product_name,
  p.spec_model,
  p.brand,
  p.manufacturer_id,
  (SELECT MIN(category_id) FROM `product` WHERE deleted = 0),
  '个', 0, 1, '个', 1, 1, 0
FROM (
  SELECT 990001 AS product_id, 'TEST_LONG_P001' AS product_code,
         '一次性使用无菌注射器带针 5ml 玻璃材质 医用透明包装 单支独立包装' AS product_name,
         '5ml 玻璃 带针 独立无菌包装' AS spec_model, '康德莱' AS brand,
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG_MFR_001' LIMIT 1) AS manufacturer_id
  UNION ALL
  SELECT 990002, 'TEST_LONG_P002',
         '一次性使用无菌注射器带针 10ml 塑料材质 医用透明包装 批量装 100支/盒',
         '10ml 塑料 带针 100支/盒', '康德莱',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG_MFR_001' LIMIT 1)
  UNION ALL
  SELECT 990003, 'TEST_LONG_P003',
         '聚维酮碘消毒剂皮肤外用消毒液 500ml 棕色瓶装 医院感染管理专用',
         '500ml 棕色瓶装', '华东医用',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG_MFR_002' LIMIT 1)
) p
WHERE NOT EXISTS (
  SELECT 1 FROM `product` existing WHERE existing.product_code = p.product_code
);

-- 绑定到第一个可用科室 + 库房，形成目录条目
-- 说明：科室表为 sys_dept（不是 department）
INSERT IGNORE INTO `department_warehouse_catalog` (`dept_id`, `warehouse_id`, `product_id`, `source_type`, `status`)
SELECT dwc.dept_id, dwc.warehouse_id, p.product_id, 'manual', 1
FROM (
  SELECT d.dept_id, w.warehouse_id
  FROM `sys_dept` d
  JOIN `warehouse` w ON w.dept_id = d.dept_id AND w.deleted = 0
  WHERE d.deleted = 0
  ORDER BY d.dept_id ASC, w.warehouse_id ASC
  LIMIT 1
) dwc
CROSS JOIN `product` p
WHERE p.product_code LIKE 'TEST_LONG_P%'
  AND p.deleted = 0;
