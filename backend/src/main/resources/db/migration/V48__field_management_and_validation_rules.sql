-- 字段管理（表格下拉选字段字典）与业务校验规则存储
-- 1) sys_field_option: 所有表格下拉选字段的选项字典，支持字段管理模块增改
-- 2) sys_validation_rule: 后端业务校验规则的字段组合存储（如待审批目录重复校验规则）
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

-- 供应商类型下拉选项（与现有供应商数据一致）
INSERT INTO `sys_field_option` (`field_key`, `field_label`, `option_value`, `option_label`, `sort_order`) VALUES
('supplier_type', '供应商类型', '配送商', '配送商', 10),
('supplier_type', '供应商类型', '经销商', '经销商', 20),
('supplier_type', '供应商类型', '生产商', '生产商', 30);

-- 供应商等级下拉选项
INSERT INTO `sys_field_option` (`field_key`, `field_label`, `option_value`, `option_label`, `sort_order`) VALUES
('supplier_grade', '供应商等级', '', '未评级', 10),
('supplier_grade', '供应商等级', 'A', 'A 级', 20),
('supplier_grade', '供应商等级', 'B', 'B 级', 30),
('supplier_grade', '供应商等级', 'C', 'C 级', 40),
('supplier_grade', '供应商等级', 'D', 'D 级', 50);

-- 待审批目录重复校验规则：商品名称 + 规格型号 + 生产厂家 + 供应商 + 注册证号 完全一致视为重复
INSERT INTO `sys_validation_rule` (`rule_code`, `rule_name`, `biz_scope`, `rule_fields`, `remark`) VALUES
('pending_product_duplicate', '待审批目录重复校验', 'pending-product-catalog',
 'product_name,spec_model,manufacturer_name,supplier_name,registration_no',
 '商品名称+规格型号+生产厂家+供应商+注册证号完全一致时视为重复商品目录');

-- 字段管理功能权限（读 + 写），并授予 admin 角色
INSERT IGNORE INTO `sys_permission` (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`) VALUES
(0, '字段管理', 'field-option-management', 1, '/features/field-option-management', 76),
(0, '字段管理写操作', 'field-option-management:write', 2, NULL, 76);

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'admin' AND r.deleted = 0 AND p.deleted = 0
   AND p.perm_code IN ('field-option-management', 'field-option-management:write');
