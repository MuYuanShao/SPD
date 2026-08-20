CREATE TABLE IF NOT EXISTS `warehouse_product_binding` (
  `binding_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库房商品绑定ID',
  `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '库房ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`binding_id`),
  UNIQUE KEY `uk_warehouse_product_binding` (`warehouse_id`, `product_id`),
  KEY `idx_warehouse_product_product` (`product_id`),
  KEY `idx_warehouse_product_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库房可管理商品绑定';

INSERT IGNORE INTO `warehouse_product_binding` (`warehouse_id`, `product_id`, `status`)
SELECT DISTINCT warehouse_id, product_id, 1
  FROM inventory_balance
 WHERE available_qty > 0;

INSERT IGNORE INTO `warehouse_product_binding` (`warehouse_id`, `product_id`, `status`)
SELECT DISTINCT warehouse_id, product_id, 1
  FROM department_warehouse_catalog
 WHERE deleted = 0
   AND status = 1;
