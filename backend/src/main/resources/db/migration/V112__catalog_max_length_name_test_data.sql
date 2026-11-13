-- =====================================================================
-- 科室库房目录：最终极限长度测试数据（58 / 61 / 63 / 65 字符档）
-- ---------------------------------------------------------------------
-- 目的：把「商品名称」推到 58~65 字符、把「生产厂家」推到 32~37 字符，
--      覆盖完整工商注册表述与超长商品名，验证：
--        1. 长文本列在窄列宽下的折行是否稳定（不撑破表格、不重叠）
--        2. 表头分割线是否仍与各行列分隔线完全对齐
--        3. 三档字号（标准 / 大 / 特大）下的换行表现
-- 说明：
--   - 全部以 TEST_LONG4_ / TEST_LONG4_MFR_ 前缀标记，便于清理
--   - product_id 使用 993001+ 区间，避开 V109 / V110 / V111 的区间
--   - 厂家 credit_code 为 VARCHAR(18)，使用 18 位测试值
--   - 派生表每一列都显式取别名，保证 MySQL 能正确解析 p.xxx
-- =====================================================================

-- 极限长度厂家（32 / 37 / 33 字符）
INSERT INTO `manufacturer` (`manufacturer_code`, `manufacturer_name`, `credit_code`, `status`, `deleted`)
VALUES
  ('TEST_LONG4_MFR_001', '山东威高集团医用高分子制品股份有限公司山东省烟台市生产基地分公司', '91370000TESTLNG4A1', 1, 0),
  ('TEST_LONG4_MFR_002', '江苏华东医用高分子材料集团有限公司江苏省苏州市工业园区第二生产基地有限公司', '91320000TESTLNG4A2', 1, 0),
  ('TEST_LONG4_MFR_003', '河南省安阳市医疗器械股份有限公司第三生产分公司无菌车间洁净生产二部', '41050000TESTLNG4A3', 1, 0)
ON DUPLICATE KEY UPDATE `manufacturer_name` = VALUES(`manufacturer_name`), `deleted` = 0;

-- 极限长度商品（58 / 61 / 55 / 63 / 65 / 63 字符六档）
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
  SELECT 993001 AS product_id,
         'TEST_LONG4_P001' AS product_code,
         '一次性使用无菌自毁型固定剂量疫苗注射器带针 0.5ml 医用高分子透明环氧乙烷灭菌独立包装 临床急救强化型 规格合规' AS product_name,
         '0.5ml 自毁型 EO 灭菌' AS spec_model,
         '威高' AS brand,
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG4_MFR_001' LIMIT 1) AS manufacturer_id
  UNION ALL
  SELECT 993002 AS product_id,
         'TEST_LONG4_P002' AS product_code,
         '一次性使用无菌带针注射器 5ml 玻璃材质 医用透明包装 单支独立包装 适用于成人静脉注射与小儿肌内注射 一次性使用后销毁' AS product_name,
         '5ml 玻璃 带针 单支独立' AS spec_model,
         '威高' AS brand,
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG4_MFR_001' LIMIT 1) AS manufacturer_id
  UNION ALL
  SELECT 993003 AS product_id,
         'TEST_LONG4_P003' AS product_code,
         '聚维酮碘消毒剂皮肤外用消毒液 五百毫升棕色瓶装医院感染管理科专用消毒供应中心统一配送院内二次分发专用 避光保存' AS product_name,
         '500ml 棕色瓶装 院内配送' AS spec_model,
         '华东医用' AS brand,
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG4_MFR_002' LIMIT 1) AS manufacturer_id
  UNION ALL
  SELECT 993004 AS product_id,
         'TEST_LONG4_P004' AS product_code,
         '一次性使用无菌手术衣 手术帽 口罩 手术垫单 组合套裝 医院消毒供应中心清洗灭菌后独立包装供应（含灭菌有效期标识与批次追溯码）' AS product_name,
         '组合套裝 独立包装 含灭菌标识' AS spec_model,
         '华东医用' AS brand,
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG4_MFR_002' LIMIT 1) AS manufacturer_id
  UNION ALL
  SELECT 993005 AS product_id,
         'TEST_LONG4_P005' AS product_code,
         '一次性使用无菌手术包 一次性使用无菌器械包 医院消毒供应中心清洗灭菌后独立包装 一次性使用后销毁 不得重复使用 需按失效期先进先出' AS product_name,
         '一次性无菌器械包 独立包装' AS spec_model,
         '安阳医械' AS brand,
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG4_MFR_003' LIMIT 1) AS manufacturer_id
  UNION ALL
  SELECT 993006 AS product_id,
         'TEST_LONG4_P006' AS product_code,
         '医用外科缝合线 3-0 尼龙线 单针单线 一次性使用 无菌包装 适用于皮肤软组织缝合 骨科缝合 器械盒内五套装 灭菌有效期两年' AS product_name,
         '3-0 单针单线 五套装' AS spec_model,
         '华东医用' AS brand,
         (SELECT manufacturer_id FROM `manufacturer` WHERE manufacturer_code = 'TEST_LONG4_MFR_002' LIMIT 1) AS manufacturer_id
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
WHERE p.product_code LIKE 'TEST_LONG4_P%'
  AND p.deleted = 0;
