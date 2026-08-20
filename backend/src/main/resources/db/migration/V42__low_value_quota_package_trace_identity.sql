DROP PROCEDURE IF EXISTS `spd_prepare_quota_trace_identity`;

DELIMITER $$
CREATE PROCEDURE `spd_prepare_quota_trace_identity`()
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'quota_package_label'
       AND COLUMN_NAME = 'trace_code_id'
  ) THEN
    ALTER TABLE `quota_package_label`
      ADD COLUMN `trace_code_id` BIGINT UNSIGNED NULL
        COMMENT 'Stable low-value quota package trace identity' AFTER `label_no`;
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'quota_package_label'
       AND INDEX_NAME = 'uk_quota_package_trace_code'
  ) THEN
    ALTER TABLE `quota_package_label`
      ADD UNIQUE KEY `uk_quota_package_trace_code` (`trace_code_id`);
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.REFERENTIAL_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'quota_package_label'
       AND CONSTRAINT_NAME = 'fk_quota_package_trace_code'
  ) THEN
    ALTER TABLE `quota_package_label`
      ADD CONSTRAINT `fk_quota_package_trace_code`
        FOREIGN KEY (`trace_code_id`) REFERENCES `udi_trace_code` (`trace_code_id`);
  END IF;
END$$
DELIMITER ;

CALL `spd_prepare_quota_trace_identity`();
DROP PROCEDURE `spd_prepare_quota_trace_identity`;

INSERT IGNORE INTO `udi_trace_code` (
  `udi_code`, `unique_code`, `trace_scope`, `package_label_no`,
  `template_code`, `template_name`, `package_quantity`, `package_unit`, `package_status`,
  `product_code`, `product_name`, `spec_model`, `manufacturer_name`, `supplier_name`,
  `batch_no`, `expire_date`, `current_location`, `current_department`,
  `current_status`, `risk_level`, `last_event_name`, `last_event_time`
)
SELECT CONCAT('QP-', qpl.label_no), qpl.label_no, 'low_value_quota_pack', qpl.label_no,
       qpt.template_code, qpt.template_name, qpl.package_quantity, p.unit, qpl.status,
       p.product_code, p.product_name, p.spec_model, m.manufacturer_name, s.supplier_name,
       ib.system_batch_no, ib.expire_date, w.warehouse_name, d.dept_name,
       CASE qpl.status
         WHEN 'delivered' THEN 'delivered'
         WHEN 'signed' THEN 'signed'
         WHEN 'consumed' THEN 'consumed'
         WHEN 'settled' THEN 'settled'
         ELSE 'in_stock'
       END,
       'normal', '定数包追溯身份建立', qpl.create_time
  FROM quota_package_label qpl
  JOIN quota_package_template qpt ON qpt.template_id = qpl.template_id
  JOIN product p ON p.product_id = qpl.product_id
  JOIN warehouse w ON w.warehouse_id = qpl.warehouse_id
  LEFT JOIN sys_dept d ON d.dept_id = w.dept_id
  LEFT JOIN manufacturer m ON m.manufacturer_id = p.manufacturer_id
  LEFT JOIN supplier s ON s.supplier_id = p.supplier_id
  LEFT JOIN quota_package_label_source qpls
    ON qpls.source_id = (
      SELECT MIN(source_id) FROM quota_package_label_source source_min
       WHERE source_min.label_id = qpl.label_id
    )
  LEFT JOIN inventory_batch ib ON ib.batch_id = qpls.batch_id
 WHERE qpl.trace_code_id IS NULL;

UPDATE quota_package_label qpl
JOIN udi_trace_code tc
  ON tc.package_label_no = qpl.label_no
 AND tc.trace_scope = 'low_value_quota_pack'
SET qpl.trace_code_id = tc.trace_code_id
WHERE qpl.trace_code_id IS NULL;
