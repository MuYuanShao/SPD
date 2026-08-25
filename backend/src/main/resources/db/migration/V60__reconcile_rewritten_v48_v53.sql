-- Reconcile databases that already applied the former V48-V53 sequence before
-- the upstream history was rewritten with different migrations at those versions.
--
-- This migration intentionally repeats the effects of the rewritten V48-V53
-- using idempotent DDL/DML. After it succeeds, Flyway repair can safely align
-- the historical checksums because both migration histories are represented.

CREATE TABLE IF NOT EXISTS `sys_field_option` (
  `option_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `field_key` VARCHAR(64) NOT NULL COMMENT '字段键',
  `field_label` VARCHAR(100) NOT NULL COMMENT '字段名称',
  `option_value` VARCHAR(100) NOT NULL COMMENT '选项值',
  `option_label` VARCHAR(100) NOT NULL COMMENT '选项显示名',
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `remark` VARCHAR(255) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`option_id`),
  UNIQUE KEY `uk_field_option_value` (`field_key`, `option_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表格下拉选字段选项字典';

CREATE TABLE IF NOT EXISTS `sys_validation_rule` (
  `rule_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `rule_code` VARCHAR(64) NOT NULL COMMENT '规则编码',
  `rule_name` VARCHAR(100) NOT NULL COMMENT '规则名称',
  `biz_scope` VARCHAR(64) NOT NULL COMMENT '业务范围',
  `rule_fields` VARCHAR(500) NOT NULL COMMENT '逗号分隔的字段键',
  `status` TINYINT NOT NULL DEFAULT 1,
  `remark` VARCHAR(255) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`rule_id`),
  UNIQUE KEY `uk_validation_rule_code` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务校验规则字段组合存储';

INSERT INTO `sys_field_option`
  (`field_key`, `field_label`, `option_value`, `option_label`, `sort_order`)
VALUES
  ('supplier_type', '供应商类型', '配送商', '配送商', 10),
  ('supplier_type', '供应商类型', '经销商', '经销商', 20),
  ('supplier_type', '供应商类型', '生产商', '生产商', 30),
  ('supplier_grade', '供应商等级', '', '未评级', 10),
  ('supplier_grade', '供应商等级', 'A', 'A 级', 20),
  ('supplier_grade', '供应商等级', 'B', 'B 级', 30),
  ('supplier_grade', '供应商等级', 'C', 'C 级', 40),
  ('supplier_grade', '供应商等级', 'D', 'D 级', 50),
  ('receiving_type', '收货类型', 'normal', '正常收货', 10),
  ('receiving_type', '收货类型', 'return', '退货收货', 20),
  ('receiving_type', '收货类型', 'exchange', '换货收货', 30),
  ('receiving_type', '收货类型', 'agent', '代理商直送', 40)
ON DUPLICATE KEY UPDATE
  `field_label` = VALUES(`field_label`),
  `option_label` = VALUES(`option_label`),
  `sort_order` = VALUES(`sort_order`);

INSERT INTO `sys_validation_rule`
  (`rule_code`, `rule_name`, `biz_scope`, `rule_fields`, `remark`)
VALUES
  ('pending_product_duplicate', '待审批目录重复校验', 'pending-product-catalog',
   'product_name,spec_model,manufacturer_name,supplier_name,registration_no',
   '商品名称+规格型号+生产厂家+供应商+注册证号完全一致时视为重复商品目录')
ON DUPLICATE KEY UPDATE
  `rule_name` = VALUES(`rule_name`),
  `biz_scope` = VALUES(`biz_scope`),
  `rule_fields` = VALUES(`rule_fields`),
  `remark` = VALUES(`remark`);

INSERT IGNORE INTO `sys_permission`
  (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`)
VALUES
  (0, '字段管理', 'field-option-management', 1, '/features/field-option-management', 76),
  (0, '字段管理写操作', 'field-option-management:write', 2, NULL, 76);

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id
  FROM sys_role r
 CROSS JOIN sys_permission p
 WHERE r.role_code = 'admin'
   AND r.deleted = 0
   AND p.deleted = 0
   AND p.perm_code IN ('field-option-management', 'field-option-management:write');

DROP PROCEDURE IF EXISTS spd_v60_add_column_if_not_exists;
DELIMITER //
CREATE PROCEDURE spd_v60_add_column_if_not_exists(
    IN p_table_name VARCHAR(100),
    IN p_column_name VARCHAR(100),
    IN p_column_def TEXT
)
BEGIN
    DECLARE col_count INT DEFAULT 0;
    SELECT COUNT(*) INTO col_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_table_name
       AND COLUMN_NAME = p_column_name;
    IF col_count = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_column_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL spd_v60_add_column_if_not_exists(
  'supplier', 'business_license_no',
  'VARCHAR(80) NULL COMMENT ''经营许可证号'' AFTER `credit_code`'
);
CALL spd_v60_add_column_if_not_exists(
  'product', 'purchase_package_qty',
  'DECIMAL(18,3) NULL COMMENT ''采购包装数量'' AFTER `conversion_rate`'
);
CALL spd_v60_add_column_if_not_exists(
  'pending_product_application', 'purchase_package_qty',
  'DECIMAL(18,3) NULL COMMENT ''采购包装数量'' AFTER `conversion_rate`'
);
CALL spd_v60_add_column_if_not_exists(
  'quota_packing_task_reservation', 'receiving_no',
  'VARCHAR(50) NULL COMMENT ''来源验收单号'' AFTER `batch_id`'
);
CALL spd_v60_add_column_if_not_exists(
  'quota_packing_task_reservation', 'receiving_item_id',
  'BIGINT UNSIGNED NULL COMMENT ''来源验收明细ID'' AFTER `receiving_no`'
);
CALL spd_v60_add_column_if_not_exists(
  'receiving_order', 'receiving_type',
  'VARCHAR(30) NULL COMMENT ''收货类型'' AFTER `receiving_status`'
);
CALL spd_v60_add_column_if_not_exists(
  'receiving_order', 'is_agent',
  'TINYINT NOT NULL DEFAULT 0 COMMENT ''是否代理商'' AFTER `receiving_type`'
);

-- The former migration history already created receiving_type with a legacy
-- NOT NULL default. Align its shape with the rewritten V52 contract while
-- preserving all existing values.
ALTER TABLE `receiving_order`
  MODIFY `receiving_type` VARCHAR(30) NULL COMMENT '收货类型';

DROP PROCEDURE IF EXISTS spd_v60_add_column_if_not_exists;

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.user_id, r.role_id
  FROM sys_user u
  JOIN sys_role r
    ON r.role_code = 'operator'
   AND r.deleted = 0
   AND r.status = 1
 WHERE u.username = 'operator01'
   AND u.deleted = 0;
