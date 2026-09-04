ALTER TABLE `inventory_event`
  ADD COLUMN `transaction_type_code` VARCHAR(40) NULL COMMENT 'Stable ledger transaction type' AFTER `event_type`,
  ADD COLUMN `event_category` VARCHAR(20) NOT NULL DEFAULT 'quantity' COMMENT 'quantity or valuation' AFTER `transaction_type_code`,
  ADD COLUMN `dept_id_snapshot` BIGINT UNSIGNED NULL COMMENT 'Department at event time' AFTER `source_biz_id`,
  ADD COLUMN `dept_name_snapshot` VARCHAR(100) NULL COMMENT 'Department name at event time' AFTER `dept_id_snapshot`,
  ADD COLUMN `warehouse_code_snapshot` VARCHAR(50) NULL COMMENT 'Warehouse code at event time' AFTER `warehouse_id`,
  ADD COLUMN `warehouse_name_snapshot` VARCHAR(100) NULL COMMENT 'Warehouse name at event time' AFTER `warehouse_code_snapshot`,
  ADD COLUMN `product_code_snapshot` VARCHAR(64) NULL COMMENT 'Product code at event time' AFTER `product_id`,
  ADD COLUMN `product_name_snapshot` VARCHAR(255) NULL COMMENT 'Product name at event time' AFTER `product_code_snapshot`,
  ADD COLUMN `spec_model_snapshot` VARCHAR(255) NULL COMMENT 'Specification at event time' AFTER `product_name_snapshot`,
  ADD COLUMN `registration_no_snapshot` VARCHAR(120) NULL COMMENT 'Registration number at event time' AFTER `spec_model_snapshot`,
  ADD COLUMN `unit_snapshot` VARCHAR(30) NULL COMMENT 'Unit at event time' AFTER `registration_no_snapshot`,
  ADD COLUMN `manufacturer_name_snapshot` VARCHAR(255) NULL COMMENT 'Manufacturer at event time' AFTER `unit_snapshot`,
  ADD COLUMN `supplier_id_snapshot` BIGINT UNSIGNED NULL COMMENT 'Batch supplier at event time' AFTER `manufacturer_name_snapshot`,
  ADD COLUMN `supplier_name_snapshot` VARCHAR(255) NULL COMMENT 'Batch supplier name at event time' AFTER `supplier_id_snapshot`,
  ADD COLUMN `system_batch_no_snapshot` VARCHAR(50) NULL COMMENT 'System batch at event time' AFTER `batch_id`,
  ADD COLUMN `production_batch_no_snapshot` VARCHAR(50) NULL COMMENT 'Production batch at event time' AFTER `system_batch_no_snapshot`,
  ADD COLUMN `unit_price_snapshot` DECIMAL(18,4) NULL COMMENT 'Unit price at event time' AFTER `qty_after`,
  ADD COLUMN `amount_snapshot` DECIMAL(18,4) NULL COMMENT 'Signed event amount at event time' AFTER `unit_price_snapshot`,
  ADD COLUMN `old_unit_price` DECIMAL(18,4) NULL COMMENT 'Old price for valuation event' AFTER `amount_snapshot`,
  ADD COLUMN `new_unit_price` DECIMAL(18,4) NULL COMMENT 'New price for valuation event' AFTER `old_unit_price`,
  ADD COLUMN `affected_qty_snapshot` DECIMAL(18,4) NULL COMMENT 'Quantity affected by valuation event' AFTER `new_unit_price`,
  ADD COLUMN `value_change` DECIMAL(18,4) NULL COMMENT 'Signed valuation change' AFTER `affected_qty_snapshot`,
  ADD COLUMN `snapshot_origin` VARCHAR(30) NOT NULL DEFAULT 'legacy_backfill' COMMENT 'captured or legacy_backfill' AFTER `value_change`,
  ADD KEY `idx_inventory_event_dept_time` (`dept_id_snapshot`, `event_time`, `event_id`),
  ADD KEY `idx_inventory_event_transaction_time` (`transaction_type_code`, `event_time`, `event_id`);

UPDATE `inventory_event` ie
JOIN `warehouse` w ON w.`warehouse_id` = ie.`warehouse_id`
LEFT JOIN `sys_dept` d ON d.`dept_id` = w.`dept_id`
JOIN `product` p ON p.`product_id` = ie.`product_id`
LEFT JOIN `manufacturer` m ON m.`manufacturer_id` = p.`manufacturer_id`
JOIN `inventory_batch` ib ON ib.`batch_id` = ie.`batch_id`
LEFT JOIN `supplier` s ON s.`supplier_id` = ib.`supplier_id`
SET ie.`dept_id_snapshot` = w.`dept_id`,
    ie.`dept_name_snapshot` = d.`dept_name`,
    ie.`warehouse_code_snapshot` = w.`warehouse_code`,
    ie.`warehouse_name_snapshot` = w.`warehouse_name`,
    ie.`product_code_snapshot` = p.`product_code`,
    ie.`product_name_snapshot` = p.`product_name`,
    ie.`spec_model_snapshot` = p.`spec_model`,
    ie.`registration_no_snapshot` = p.`registration_no`,
    ie.`unit_snapshot` = p.`unit`,
    ie.`manufacturer_name_snapshot` = m.`manufacturer_name`,
    ie.`supplier_id_snapshot` = ib.`supplier_id`,
    ie.`supplier_name_snapshot` = s.`supplier_name`,
    ie.`system_batch_no_snapshot` = ib.`system_batch_no`,
    ie.`production_batch_no_snapshot` = ib.`production_batch_no`,
    ie.`unit_price_snapshot` = COALESCE((
      SELECT pa.`old_unit_price`
        FROM `batch_price_adjustment` pa
       WHERE pa.`batch_id` = ie.`batch_id`
         AND pa.`status` = 'approved'
         AND pa.`approve_time` > ie.`event_time`
       ORDER BY pa.`approve_time`, pa.`adjustment_id`
       LIMIT 1
    ), ib.`batch_unit_price`),
    ie.`snapshot_origin` = 'legacy_backfill';

UPDATE `inventory_event`
SET `amount_snapshot` = ROUND(`qty_change` * `unit_price_snapshot`, 4),
    `transaction_type_code` = CASE
      WHEN `event_type` = 'purchase_receive_in' THEN 'receiving_in'
      WHEN `event_type` = 'quota_pack_out' THEN 'quota_pack_in'
      WHEN `event_type` IN ('quota_unpack_in', 'quota_terminate_in') THEN 'quota_unpack'
      WHEN `qty_change` > 0 AND `warehouse_id` IN (SELECT warehouse_id FROM warehouse WHERE warehouse_type LIKE '%三级%') THEN 'tertiary_in'
      WHEN `qty_change` < 0 AND `warehouse_id` IN (SELECT warehouse_id FROM warehouse WHERE warehouse_type LIKE '%三级%') THEN 'tertiary_out'
      WHEN `qty_change` > 0 AND `warehouse_id` IN (SELECT warehouse_id FROM warehouse WHERE warehouse_type LIKE '%二级%') THEN 'secondary_in'
      WHEN `qty_change` < 0 AND `warehouse_id` IN (SELECT warehouse_id FROM warehouse WHERE warehouse_type LIKE '%二级%') THEN 'secondary_out'
      WHEN `qty_change` > 0 THEN 'receiving_in'
      ELSE 'secondary_out'
    END;

CREATE TABLE `inventory_event_trace_code` (
  `event_id` BIGINT UNSIGNED NOT NULL COMMENT 'Immutable inventory event ID',
  `trace_code_id` BIGINT UNSIGNED NOT NULL COMMENT 'Stable UDI or package trace identity',
  `trace_type` VARCHAR(40) NOT NULL COMMENT 'high_value_unit or quota_package',
  `linked_quantity` DECIMAL(18,4) NOT NULL DEFAULT 1 COMMENT 'Quantity represented by this trace identity',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`event_id`, `trace_code_id`),
  KEY `idx_inventory_event_trace_code` (`trace_code_id`),
  CONSTRAINT `fk_inventory_event_trace_event` FOREIGN KEY (`event_id`) REFERENCES `inventory_event` (`event_id`),
  CONSTRAINT `fk_inventory_event_trace_identity` FOREIGN KEY (`trace_code_id`) REFERENCES `udi_trace_code` (`trace_code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Stable trace identities owned by an immutable inventory event';

INSERT IGNORE INTO `inventory_event_trace_code` (`event_id`, `trace_code_id`, `trace_type`, `linked_quantity`)
SELECT ie.`event_id`, ibtc.`trace_code_id`, 'high_value_unit', 1
  FROM `inventory_event` ie
  JOIN `receiving_order_item` roi
    ON ie.`source_biz_type` = 'receiving_order'
   AND roi.`receiving_order_id` = ie.`source_biz_id`
   AND roi.`product_id` = ie.`product_id`
  JOIN `inventory_batch_trace_code` ibtc
    ON ibtc.`receiving_item_id` = roi.`item_id`
   AND ibtc.`batch_id` = ie.`batch_id`
 WHERE ie.`event_type` = 'purchase_receive_in';

INSERT IGNORE INTO `inventory_event_trace_code` (`event_id`, `trace_code_id`, `trace_type`, `linked_quantity`)
SELECT ie.`event_id`, qpl.`trace_code_id`, 'quota_package', 1
  FROM `inventory_event` ie
  JOIN `quota_package_label` qpl
    ON ie.`source_biz_type` = 'quota_package_label'
   AND qpl.`label_id` = ie.`source_biz_id`
 WHERE qpl.`trace_code_id` IS NOT NULL;

DROP TRIGGER IF EXISTS `trg_inventory_event_immutable_update`;
DROP TRIGGER IF EXISTS `trg_inventory_event_immutable_delete`;
DROP TRIGGER IF EXISTS `trg_inventory_event_trace_immutable_update`;
DROP TRIGGER IF EXISTS `trg_inventory_event_trace_immutable_delete`;

DELIMITER $$
CREATE TRIGGER `trg_inventory_event_immutable_update`
BEFORE UPDATE ON `inventory_event`
FOR EACH ROW
BEGIN
  IF COALESCE(@spd_allow_inventory_event_maintenance, 0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory events are immutable';
  END IF;
END$$
CREATE TRIGGER `trg_inventory_event_immutable_delete`
BEFORE DELETE ON `inventory_event`
FOR EACH ROW
BEGIN
  IF COALESCE(@spd_allow_inventory_event_maintenance, 0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory events are immutable';
  END IF;
END$$
CREATE TRIGGER `trg_inventory_event_trace_immutable_update`
BEFORE UPDATE ON `inventory_event_trace_code`
FOR EACH ROW
BEGIN
  IF COALESCE(@spd_allow_inventory_event_maintenance, 0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory event trace links are immutable';
  END IF;
END$$
CREATE TRIGGER `trg_inventory_event_trace_immutable_delete`
BEFORE DELETE ON `inventory_event_trace_code`
FOR EACH ROW
BEGIN
  IF COALESCE(@spd_allow_inventory_event_maintenance, 0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory event trace links are immutable';
  END IF;
END$$
DELIMITER ;
