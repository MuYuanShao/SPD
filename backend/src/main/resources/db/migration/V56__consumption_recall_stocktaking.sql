-- 第二批需求：召回事件记录批次、盘点表记录科室。
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

DROP PROCEDURE IF EXISTS spd_add_index_if_not_exists;
DELIMITER //
CREATE PROCEDURE spd_add_index_if_not_exists(
    IN p_table_name VARCHAR(100),
    IN p_index_name VARCHAR(100),
    IN p_index_def TEXT
)
BEGIN
    DECLARE idx_count INT DEFAULT 0;
    SELECT COUNT(*) INTO idx_count
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND INDEX_NAME = p_index_name;
    IF idx_count = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD INDEX ', p_index_name, ' ', p_index_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL spd_add_col_if_not_exists('recall_event', 'batch_id', 'BIGINT UNSIGNED DEFAULT NULL COMMENT ''召回批次ID'' AFTER `product_name`');
CALL spd_add_index_if_not_exists('recall_event', 'idx_recall_batch', '(`batch_id`)');

CALL spd_add_col_if_not_exists('inventory_stocktaking', 'dept_name', 'VARCHAR(80) DEFAULT NULL COMMENT ''盘点科室'' AFTER `warehouse_id`');

DROP PROCEDURE IF EXISTS spd_add_col_if_not_exists;
DROP PROCEDURE IF EXISTS spd_add_index_if_not_exists;
