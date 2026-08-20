CREATE TABLE IF NOT EXISTS `replenishment_smart_analysis` (
  `analysis_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分析ID',
  `dept_name` VARCHAR(80) NOT NULL COMMENT '科室名称',
  `warehouse_name` VARCHAR(80) NOT NULL COMMENT '库房名称',
  `selected_period_days` INT NOT NULL COMMENT '选择周期天数',
  `item_count` INT NOT NULL DEFAULT 0 COMMENT '分析商品数',
  `total_recommended_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '建议补货总量',
  `analysis_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分析时间',
  PRIMARY KEY (`analysis_id`),
  KEY `idx_dept_warehouse_time` (`dept_name`, `warehouse_name`, `analysis_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能补货分析主表';

CREATE TABLE IF NOT EXISTS `replenishment_smart_analysis_item` (
  `item_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分析明细ID',
  `analysis_id` BIGINT UNSIGNED NOT NULL COMMENT '分析ID',
  `product_code` VARCHAR(50) NOT NULL COMMENT '商品编码',
  `product_name` VARCHAR(120) NOT NULL COMMENT '商品名称',
  `issue_5_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '5天出库数量',
  `issue_7_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '7天出库数量',
  `issue_15_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '15天出库数量',
  `issue_30_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '30天出库数量',
  `current_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '当前库存',
  `recommended_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '建议补货数量',
  `formula_text` VARCHAR(255) DEFAULT NULL COMMENT '公式说明',
  PRIMARY KEY (`item_id`),
  KEY `idx_analysis_id` (`analysis_id`),
  KEY `idx_product_code` (`product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能补货分析明细表';
