ALTER TABLE `spd_delivery_order`
  ADD COLUMN `allocation_mode` VARCHAR(30) NOT NULL DEFAULT 'loose' COMMENT '分配方式：loose=散货，quota_package=定数包' AFTER `quantity`;

CREATE TABLE IF NOT EXISTS `spd_delivery_quota_package` (
  `allocation_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配送定数包分配ID',
  `delivery_id` BIGINT UNSIGNED NOT NULL COMMENT '配送单ID',
  `label_id` BIGINT UNSIGNED NOT NULL COMMENT '定数包标签ID',
  `label_no` VARCHAR(50) NOT NULL COMMENT '定数包标签号',
  `package_quantity` DECIMAL(18,4) NOT NULL COMMENT '包内数量',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`allocation_id`),
  UNIQUE KEY `uk_delivery_quota_label` (`delivery_id`, `label_id`),
  KEY `idx_delivery_quota_delivery` (`delivery_id`),
  KEY `idx_delivery_quota_label` (`label_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配送定数包分配表';
