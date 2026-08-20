-- Add additional columns and indexes to pending_product_application table.
-- Uses MySQL prepared statements to check column existence first (idempotent).

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

CALL spd_add_col_if_not_exists('pending_product_application', 'product_name',              'VARCHAR(100) NULL COMMENT ''商品名称'' AFTER `supplier_id`');
CALL spd_add_col_if_not_exists('pending_product_application', 'product_code',              'VARCHAR(50) NULL COMMENT ''商品编码'' AFTER `product_name`');
CALL spd_add_col_if_not_exists('pending_product_application', 'spec_model',                'VARCHAR(100) NULL COMMENT ''规格型号'' AFTER `product_code`');
CALL spd_add_col_if_not_exists('pending_product_application', 'brand',                     'VARCHAR(50) NULL COMMENT ''品牌'' AFTER `spec_model`');
CALL spd_add_col_if_not_exists('pending_product_application', 'manufacturer_id',           'BIGINT UNSIGNED NULL COMMENT ''生产厂家ID'' AFTER `brand`');
CALL spd_add_col_if_not_exists('pending_product_application', 'manufacturer_name',          'VARCHAR(100) NULL COMMENT ''生产厂家名称'' AFTER `manufacturer_id`');
CALL spd_add_col_if_not_exists('pending_product_application', 'category_id',               'BIGINT UNSIGNED NULL COMMENT ''商品分类ID'' AFTER `manufacturer_name`');
CALL spd_add_col_if_not_exists('pending_product_application', 'unit',                      'VARCHAR(20) NULL COMMENT ''基本单位'' AFTER `category_id`');
CALL spd_add_col_if_not_exists('pending_product_application', 'purchase_price',            'DECIMAL(18,4) NULL COMMENT ''采购价'' AFTER `unit`');
CALL spd_add_col_if_not_exists('pending_product_application', 'retail_price',              'DECIMAL(18,4) NULL COMMENT ''零售价'' AFTER `purchase_price`');
CALL spd_add_col_if_not_exists('pending_product_application', 'min_purchase_qty',          'DECIMAL(18,4) NULL COMMENT ''最小采购量'' AFTER `retail_price`');
CALL spd_add_col_if_not_exists('pending_product_application', 'purchase_unit',             'VARCHAR(20) NULL COMMENT ''采购单位'' AFTER `min_purchase_qty`');
CALL spd_add_col_if_not_exists('pending_product_application', 'conversion_rate',           'DECIMAL(18,6) NULL COMMENT ''换算系数'' AFTER `purchase_unit`');
CALL spd_add_col_if_not_exists('pending_product_application', 'udi_code',                  'VARCHAR(100) NULL COMMENT ''UDI编码'' AFTER `conversion_rate`');
CALL spd_add_col_if_not_exists('pending_product_application', 'registration_no',           'VARCHAR(100) NULL COMMENT ''注册证号'' AFTER `udi_code`');
CALL spd_add_col_if_not_exists('pending_product_application', 'registration_expire_date',  'DATE NULL COMMENT ''注册证有效期'' AFTER `registration_no`');
CALL spd_add_col_if_not_exists('pending_product_application', 'production_license_no',     'VARCHAR(100) NULL COMMENT ''生产许可证号'' AFTER `registration_expire_date`');
CALL spd_add_col_if_not_exists('pending_product_application', 'business_license_no',       'VARCHAR(100) NULL COMMENT ''经营许可证号'' AFTER `production_license_no`');
CALL spd_add_col_if_not_exists('pending_product_application', 'qualification_attachment_count', 'INT NOT NULL DEFAULT 0 COMMENT ''资质附件数量'' AFTER `business_license_no`');
CALL spd_add_col_if_not_exists('pending_product_application', 'is_high_value',             'TINYINT NOT NULL DEFAULT 0 COMMENT ''是否高值耗材'' AFTER `qualification_attachment_count`');
CALL spd_add_col_if_not_exists('pending_product_application', 'is_cold_chain',             'TINYINT NOT NULL DEFAULT 0 COMMENT ''是否冷链'' AFTER `is_high_value`');
CALL spd_add_col_if_not_exists('pending_product_application', 'is_quota_managed',          'TINYINT NOT NULL DEFAULT 0 COMMENT ''是否定数管理'' AFTER `is_cold_chain`');
CALL spd_add_col_if_not_exists('pending_product_application', 'storage_condition',         'VARCHAR(30) NULL COMMENT ''储存条件'' AFTER `is_quota_managed`');
CALL spd_add_col_if_not_exists('pending_product_application', 'initial_review_by',         'BIGINT UNSIGNED NULL COMMENT ''初审人'' AFTER `approve_opinion`');
CALL spd_add_col_if_not_exists('pending_product_application', 'initial_review_time',       'DATETIME NULL COMMENT ''初审时间'' AFTER `initial_review_by`');
CALL spd_add_col_if_not_exists('pending_product_application', 'initial_review_opinion',    'VARCHAR(500) NULL COMMENT ''初审意见'' AFTER `initial_review_time`');
CALL spd_add_col_if_not_exists('pending_product_application', 'final_review_by',           'BIGINT UNSIGNED NULL COMMENT ''复审人'' AFTER `initial_review_opinion`');
CALL spd_add_col_if_not_exists('pending_product_application', 'final_review_time',         'DATETIME NULL COMMENT ''复审时间'' AFTER `final_review_by`');
CALL spd_add_col_if_not_exists('pending_product_application', 'final_review_opinion',      'VARCHAR(500) NULL COMMENT ''复审意见'' AFTER `final_review_time`');
CALL spd_add_col_if_not_exists('pending_product_application', 'return_reason',             'VARCHAR(500) NULL COMMENT ''退回原因'' AFTER `final_review_opinion`');
CALL spd_add_col_if_not_exists('pending_product_application', 'reject_reason',             'VARCHAR(500) NULL COMMENT ''驳回原因'' AFTER `return_reason`');

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

CALL spd_add_index_if_not_exists('pending_product_application', 'idx_product_name',           '(`product_name`)');
CALL spd_add_index_if_not_exists('pending_product_application', 'idx_product_code',           '(`product_code`)');
CALL spd_add_index_if_not_exists('pending_product_application', 'idx_application_type_status','(`application_type`, `approval_status`)');
CALL spd_add_index_if_not_exists('pending_product_application', 'idx_category_id',            '(`category_id`)');
CALL spd_add_index_if_not_exists('pending_product_application', 'idx_submit_time',            '(`submit_time`)');

DROP PROCEDURE IF EXISTS spd_add_col_if_not_exists;
DROP PROCEDURE IF EXISTS spd_add_index_if_not_exists;
