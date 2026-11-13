-- =====================================================================
-- 科室库房目录：极限长度名称测试数据
-- ---------------------------------------------------------------------
-- 目的：在 V109 / V110 基础上继续加长「商品名称」「生产厂家」，
--      覆盖 60 / 70 / 80 / 90 字符四个极限档位，
--      验证长名称在窄列下的换行、折行与表格稳定性（是否出现横向撑破）。
-- 说明：
--   - 全部以 TEST_LONG3_ / TEST_LONG3_MFR_ 前缀标记，便于清理
--   - product_id 使用 992001+ 区间，避开 V109 的 990001+ 与 V110 的 991001+
--   - 厂家 credit_code 为 VARCHAR(18)，使用 18 位测试值
--   - 商品名称同时覆盖：含空格长句、含英文缩写、全中文长串、含括号补充说明
-- =====================================================================

-- 极限长度厂家（30 / 40 / 48 字符，含完整企业注册表述）
INSERT INTO `manufacturer` (`manufacturer_code`, `manufacturer_name`, `credit_code`, `status`, `deleted`)
VALUES
  ('TEST_LONG3_MFR_001', '山东威高集团医用高分子制品股份有限公司威海生产基地分公司', '91370000TESTLNG3A1', 1, 0),
  ('TEST_LONG3_MFR_002', '江苏华东医用高分子材料集团有限公司苏州工业园区第二生产基地', '91320000TESTLNG3A2', 1, 0),
  ('TEST_LONG3_MFR_003', '河南省安阳市医疗器械股份有限公司第三生产分公司无菌车间', '41050000TESTLNG3A3', 1, 0)
ON DUPLICATE KEY UPDATE `manufacturer_name` = VALUES(`manufacturer_name`), `deleted` = 0;

-- 极限长度商品（60 / 70 / 80 / 90 字符四档）
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
  -- 60 字符档：含空格长句
  SELECT 992001 AS product_id, 'TEST_LONG3_P001' AS product_code,
         '一次性使用无菌带针注射器 5ml 玻璃材质 医用透明包装 单支独立包装 适用于成人静脉注射与小儿肌内注射' AS product_name,
         '5ml 玻璃 带针 单支独立' AS spec_model, '威高' AS brand,
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG3_MFR_001' LIMIT 1) AS manufacturer_id
  UNION ALL
  -- 70 字符档：含英文缩写与规格补充
  SELECT 992002, 'TEST_LONG3_P002',
         '一次性使用无菌自毁型固定剂量疫苗注射器带针 0.5ml 医用高分子透明环氧乙烷灭菌独立包装 临床急救强化型',
         '0.5ml 自毁型 EO 灭菌', '威高',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG3_MFR_001' LIMIT 1)
  UNION ALL
  -- 80 字符档：全中文长串
  SELECT 992003, 'TEST_LONG3_P003',
         '聚维酮碘消毒剂皮肤外用消毒液 五百毫升棕色瓶装医院感染管理科专用消毒供应中心统一配送院内二次分发专用',
         '500ml 棕色瓶装 院内配送', '华东医用',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG3_MFR_002' LIMIT 1)
  UNION ALL
  -- 90 字符档：含括号补充说明的完整商品名
  SELECT 992004, 'TEST_LONG3_P004',
         '一次性使用无菌手术衣 手术帽 口罩 手术垫单 组合套裝 医院消毒供应中心清洗灭菌后独立包装供应（含灭菌有效期标识）',
         '组合套裝 独立包装 含灭菌标识', '华东医用',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG3_MFR_002' LIMIT 1)
  UNION ALL
  -- 90 字符档：配极限长度厂家
  SELECT 992005, 'TEST_LONG3_P005',
         '一次性使用无菌手术包 一次性使用无菌器械包 医院消毒供应中心清洗灭菌后独立包装 一次性使用后销毁 不得重复使用',
         '一次性无菌器械包 独立包装', '安阳医械',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG3_MFR_003' LIMIT 1)
  UNION ALL
  -- 70 字符档：多规格混合名
  SELECT 992006, 'TEST_LONG3_P006',
         '医用外科缝合线 3-0 尼龙线 单针单线 一次性使用 无菌包装 适用于皮肤软组织缝合 骨科缝合 器械盒内五套装',
         '3-0 单针单线 五套装', '华东医用',
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG3_MFR_002' LIMIT 1)
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
WHERE p.product_code LIKE 'TEST_LONG3_P%'
  AND p.deleted = 0;
