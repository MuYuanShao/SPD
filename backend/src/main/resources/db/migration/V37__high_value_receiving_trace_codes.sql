CREATE TABLE IF NOT EXISTS `inventory_batch_trace_code` (
  `batch_id` BIGINT UNSIGNED NOT NULL COMMENT 'Inventory batch ID',
  `trace_code_id` BIGINT UNSIGNED NOT NULL COMMENT 'Stable UDI trace code ID',
  `receiving_item_id` BIGINT UNSIGNED NOT NULL COMMENT 'Receiving item ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  PRIMARY KEY (`trace_code_id`),
  UNIQUE KEY `uk_inventory_batch_trace_code` (`batch_id`, `trace_code_id`),
  KEY `idx_inventory_trace_batch` (`batch_id`),
  KEY `idx_inventory_trace_receiving_item` (`receiving_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='High-value item trace codes bound to receiving inventory batches';
