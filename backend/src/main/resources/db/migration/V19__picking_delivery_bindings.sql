CREATE TABLE IF NOT EXISTS `spd_delivery_package_binding` (
  `binding_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '拣配绑定ID',
  `delivery_id` BIGINT UNSIGNED NOT NULL COMMENT '配送单ID',
  `requisition_id` BIGINT UNSIGNED NOT NULL COMMENT '科室申领单ID',
  `requisition_item_id` BIGINT UNSIGNED NOT NULL COMMENT '科室申领明细ID',
  `label_id` BIGINT UNSIGNED NOT NULL COMMENT '定数包标签ID',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `package_quantity` DECIMAL(18,4) NOT NULL COMMENT '绑定数量',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`binding_id`),
  UNIQUE KEY `uk_delivery_label` (`label_id`),
  KEY `idx_delivery_id` (`delivery_id`),
  KEY `idx_requisition_id` (`requisition_id`),
  KEY `idx_requisition_item_id` (`requisition_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拣配配送定数包绑定表';
