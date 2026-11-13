-- =====================================================================
-- 科室库房目录：超长名称测试数据（分档）
-- ---------------------------------------------------------------------
-- 目的：在 V109 基础上继续加长「商品名称」「生产厂家」，
--      覆盖 30 / 40 / 55 / 70 字符四个长度档位，
--      用于验证不同列宽策略下换行与折行的可用性。
-- 说明：
--   - 全部以 TEST_LONG2_ / TEST_LONG2_MFR_ 前缀标记，便于清理
--   - product_id 使用 991001+ 区间，避开 V109 的 990001+ 与既有种子数据
--   - 厂家 credit_code 为 VARCHAR(18)，使用 18 位测试值
-- =====================================================================

-- 超长名称厂家（27 / 34 / 42 字符）
INSERT INTO `manufacturer` (`manufacturer_code`, `manufacturer_name`, `credit_code`, `status`, `deleted`)
VALUES
  ('TEST_LONG2_MFR_001', '山东威高集团医用高分子制品股份有限公司', '91370000TESTLNG2A1', 1, 0),
  ('TEST_LONG2_MFR_002', '江苏华东医用高分子材料集团有限公司苏州第一分公司', '91320000TESTLNG2A2', 1, 0),
  ('TEST_LONG2_MFR_003', '河南省安阳市医疗器械股份有限公司第三生产分公司', '41050000TESTLNG2A3', 1, 0)
ON DUPLICATE KEY UPDATE `manufacturer_name` = VALUES(`manufacturer_name`), `deleted` = 0;

-- 超长名称商品（30 / 42 / 55 / 70 字符四档）
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
  -- 30 字符档
  SELECT 991001 AS product_id, 'TEST_LONG2_P001' AS product_code,
         '一次性使用无菌注射器带针 5ml 玻璃材质 独立包装' AS product_name,
         '5ml 玻璃 带针' AS spec_model, '威高' AS brand,
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG2_MFR_001' LIMIT 1) AS manufacturer_id
  UNION ALL
  -- 42 字符档
  SELECT 991002, 'TEST_LONG2_P002',
         '一次性使用无菌注射器带针 10ml 塑料材质 医用透明包装 批量装 100支/盒',
         '10ml 塑料 带针 100支/盒', '威高',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG2_MFR_001' LIMIT 1)
  UNION ALL
  -- 55 字符档
  SELECT 991003, 'TEST_LONG2_P003',
         '聚维酮碘消毒剂皮肤外用消毒液 500ml 棕色瓶装 医院感染管理科专用 消毒供应中心统一配送',
         '500ml 棕色瓶装', '华东医用',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG2_MFR_002' LIMIT 1)
  UNION ALL
  -- 70 字符档
  SELECT 991004, 'TEST_LONG2_P004',
         '一次性使用无菌带针注射器 5ml 玻璃材质 医用透明包装 单支独立包装 适用于成人静脉注射 一次性使用后销毁',
         '5ml 玻璃 带针 单支独立', '威高',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG2_MFR_001' LIMIT 1)
  UNION ALL
  -- 55 字符档（配超长厂家）
  SELECT 991005, 'TEST_LONG2_P005',
         '医用外科缝合线 3-0 尼龙线 单针单线 一次性使用 无菌包装 适用于皮肤及软组织缝合',
         '3-0 单针单线', '华东医用',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG2_MFR_002' LIMIT 1)
  UNION ALL
  -- 70 字符档（配超长厂家）
  SELECT 991006, 'TEST_LONG2_P006',
         '一次性使用无菌手术衣 手术帽 口罩 手术垫单 组合套裝 医院消毒供应中心清洗灭菌后独立包装供应',
         '组合套裝 独立包装', '华东医用',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG2_MFR_003' LIMIT 1)
) p
WHERE NOT EXISTS (
  SELECT 1 FROM `product` existing WHERE existing.product_code = p.product_code
);

-- 绑定到第一个可用科室 + 库房，形成目录条目
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
WHERE p.product_code LIKE 'TEST_LONG2_P%'
  AND p.deleted = 0;
