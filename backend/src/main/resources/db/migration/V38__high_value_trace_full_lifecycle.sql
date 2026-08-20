DROP PROCEDURE IF EXISTS spd_add_trace_column_if_missing;
DELIMITER $$
CREATE PROCEDURE spd_add_trace_column_if_missing(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL spd_add_trace_column_if_missing('inventory_batch_trace_code', 'current_warehouse_id', 'BIGINT UNSIGNED NULL COMMENT ''Current warehouse ID'' AFTER `receiving_item_id`');
CALL spd_add_trace_column_if_missing('inventory_batch_trace_code', 'lifecycle_status', 'VARCHAR(40) NOT NULL DEFAULT ''in_stock'' COMMENT ''Unit lifecycle status'' AFTER `current_warehouse_id`');
CALL spd_add_trace_column_if_missing('department_consumption_item', 'trace_code_id', 'BIGINT UNSIGNED NULL COMMENT ''Stable high-value unit identity'' AFTER `batch_id`');
CALL spd_add_trace_column_if_missing('settlement_bill_item', 'trace_code_id', 'BIGINT UNSIGNED NULL COMMENT ''Stable high-value unit identity'' AFTER `batch_id`');
DROP PROCEDURE IF EXISTS spd_add_trace_column_if_missing;

UPDATE `inventory_batch_trace_code` ibtc
JOIN (
  SELECT batch_id, MIN(warehouse_id) AS warehouse_id
    FROM inventory_balance
   WHERE available_qty > 0
   GROUP BY batch_id
) bal ON bal.batch_id = ibtc.batch_id
SET ibtc.current_warehouse_id = bal.warehouse_id
WHERE ibtc.current_warehouse_id IS NULL;

CREATE TABLE IF NOT EXISTS `department_requisition_trace_code` (
  `requisition_id` BIGINT UNSIGNED NOT NULL,
  `requisition_item_id` BIGINT UNSIGNED NOT NULL,
  `trace_code_id` BIGINT UNSIGNED NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`trace_code_id`),
  KEY `idx_requisition_trace_document` (`requisition_id`, `requisition_item_id`),
  CONSTRAINT `fk_requisition_trace_code` FOREIGN KEY (`trace_code_id`) REFERENCES `udi_trace_code` (`trace_code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='High-value units selected by department requisition';

CREATE TABLE IF NOT EXISTS `spd_delivery_trace_code` (
  `delivery_id` BIGINT UNSIGNED NOT NULL,
  `trace_code_id` BIGINT UNSIGNED NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`trace_code_id`),
  KEY `idx_delivery_trace_document` (`delivery_id`),
  CONSTRAINT `fk_delivery_trace_code` FOREIGN KEY (`trace_code_id`) REFERENCES `udi_trace_code` (`trace_code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='High-value units carried by delivery';


CREATE TABLE IF NOT EXISTS `udi_trace_patient_binding` (
  `binding_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `trace_code_id` BIGINT UNSIGNED NOT NULL,
  `patient_no` VARCHAR(80) NOT NULL,
  `patient_name_masked` VARCHAR(80) NULL,
  `department_name` VARCHAR(255) NULL,
  `location_name` VARCHAR(255) NULL,
  `binding_status` VARCHAR(30) NOT NULL DEFAULT 'bound',
  `bind_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`binding_id`),
  UNIQUE KEY `uk_patient_binding_trace_status` (`trace_code_id`, `binding_status`),
  KEY `idx_patient_binding_patient` (`patient_no`, `bind_time`),
  CONSTRAINT `fk_patient_binding_trace_code` FOREIGN KEY (`trace_code_id`) REFERENCES `udi_trace_code` (`trace_code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Stable high-value unit patient bindings';
