CREATE TABLE IF NOT EXISTS `department_warehouse_catalog` (
  `catalog_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '科室库房目录ID',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '科室ID',
  `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '库房ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `source_type` VARCHAR(30) NOT NULL DEFAULT 'manual' COMMENT '来源：manual/inventory/quota_template/safety_stock',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`catalog_id`),
  UNIQUE KEY `uk_dept_warehouse_product` (`dept_id`, `warehouse_id`, `product_id`, `deleted`),
  KEY `idx_dwc_warehouse` (`warehouse_id`),
  KEY `idx_dwc_product` (`product_id`),
  KEY `idx_dwc_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室库房申领目录范围';

INSERT IGNORE INTO `department_warehouse_catalog` (`dept_id`, `warehouse_id`, `product_id`, `source_type`, `status`)
SELECT DISTINCT w.dept_id, ib.warehouse_id, ib.product_id, 'inventory', 1
  FROM inventory_balance ib
  JOIN warehouse w ON w.warehouse_id = ib.warehouse_id
 WHERE w.deleted = 0
   AND w.dept_id IS NOT NULL
   AND ib.available_qty > 0;

INSERT IGNORE INTO `department_warehouse_catalog` (`dept_id`, `warehouse_id`, `product_id`, `source_type`, `status`)
SELECT DISTINCT w.dept_id, qpl.warehouse_id, qpl.product_id, 'quota_package', 1
  FROM quota_package_label qpl
  JOIN warehouse w ON w.warehouse_id = qpl.warehouse_id
 WHERE w.deleted = 0
   AND w.dept_id IS NOT NULL
   AND qpl.status = 'available';

INSERT IGNORE INTO `department_warehouse_catalog` (`dept_id`, `warehouse_id`, `product_id`, `source_type`, `status`)
SELECT DISTINCT w.dept_id, w.warehouse_id, qpti.product_id, 'quota_template', 1
  FROM quota_package_template qpt
  JOIN quota_package_template_item qpti ON qpti.template_id = qpt.template_id
  JOIN warehouse w ON w.dept_id = qpt.dept_id
 WHERE w.deleted = 0
   AND qpt.deleted = 0
   AND qpt.status = 1;

INSERT IGNORE INTO `department_warehouse_catalog` (`dept_id`, `warehouse_id`, `product_id`, `source_type`, `status`)
SELECT DISTINCT w.dept_id, w.warehouse_id, qss.product_id, 'safety_stock', 1
  FROM quota_safety_stock qss
  JOIN warehouse w ON w.dept_id = qss.dept_id
 WHERE w.deleted = 0
   AND qss.deleted = 0
   AND qss.status = 1;
