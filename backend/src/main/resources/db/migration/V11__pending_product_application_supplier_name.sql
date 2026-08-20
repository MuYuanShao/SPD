-- Store supplier text on pending applications so imports can detect duplicates
-- even when the supplier master data row is missing.

DROP PROCEDURE IF EXISTS spd_add_col_if_not_exists;
DELIMITER //
CREATE PROCEDURE spd_add_col_if_not_exists(
    IN p_table_name VARCHAR(100),
    IN p_column_name VARCHAR(100),
    IN p_column_def TEXT
)
BEGIN
    DECLARE col_count INT DEFAULT 0;
    SELECT COUNT(*) INTO col_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_column_name;
    IF col_count = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_column_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL spd_add_col_if_not_exists(
    'pending_product_application',
    'supplier_name',
    'VARCHAR(100) NULL COMMENT ''供应商名称'' AFTER `supplier_id`'
);

DROP PROCEDURE IF EXISTS spd_add_col_if_not_exists;
