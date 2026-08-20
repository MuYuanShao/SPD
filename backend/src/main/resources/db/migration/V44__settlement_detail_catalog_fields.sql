DROP PROCEDURE IF EXISTS `spd_add_settlement_catalog_column`;

DELIMITER $$
CREATE PROCEDURE `spd_add_settlement_catalog_column`(
  IN p_column_name VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'product'
       AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `product` ADD COLUMN `', p_column_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL `spd_add_settlement_catalog_column`(
  'generic_name',
  'VARCHAR(100) NULL COMMENT ''通用名称'' AFTER `product_name`'
);
CALL `spd_add_settlement_catalog_column`(
  'is_medical_insurance_payment',
  'TINYINT NOT NULL DEFAULT 0 COMMENT ''是否医保支付'' AFTER `is_chargeable`'
);
CALL `spd_add_settlement_catalog_column`(
  'volume_based_type',
  'VARCHAR(50) NULL COMMENT ''带量类型'' AFTER `is_volume_based`'
);
CALL `spd_add_settlement_catalog_column`(
  'medical_insurance_payment_type',
  'VARCHAR(50) NULL COMMENT ''医保支付类型'' AFTER `is_medical_insurance_payment`'
);

DROP PROCEDURE `spd_add_settlement_catalog_column`;

UPDATE `product`
   SET `generic_name` = `product_name`
 WHERE `generic_name` IS NULL OR TRIM(`generic_name`) = '';

UPDATE `product`
   SET `volume_based_type` = CASE
         WHEN `is_volume_based` = 1 AND `is_centralized_procurement` = 1 THEN '集中带量'
         WHEN `is_volume_based` = 1 THEN '带量采购'
         ELSE NULL
       END
 WHERE `volume_based_type` IS NULL;
