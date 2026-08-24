-- 打印模板调整：定数包标签等打印模板的字段配置与纸张设置。
CREATE TABLE IF NOT EXISTS `print_template` (
  `template_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `template_code` VARCHAR(50) NOT NULL COMMENT '模板编码',
  `template_name` VARCHAR(100) NOT NULL COMMENT '模板名称',
  `template_type` VARCHAR(30) NOT NULL COMMENT '模板类型: quota_label 定数包标签',
  `fields_json` JSON DEFAULT NULL COMMENT '字段配置 JSON',
  `paper_width_mm` DECIMAL(8,2) NOT NULL DEFAULT 100 COMMENT '纸张宽度(mm)',
  `paper_height_mm` DECIMAL(8,2) NOT NULL DEFAULT 70 COMMENT '纸张高度(mm)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0停用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_print_template_type` (`template_type`),
  KEY `idx_print_template_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打印模板表';

INSERT IGNORE INTO `print_template` (
  `template_code`, `template_name`, `template_type`, `fields_json`, `paper_width_mm`, `paper_height_mm`, `status`, `remark`
) VALUES (
  'quota-label', '定数包标签', 'quota_label',
  JSON_ARRAY(
    JSON_OBJECT('code','labelNo','label','标签号','enabled',true),
    JSON_OBJECT('code','productName','label','商品','enabled',true),
    JSON_OBJECT('code','templateName','label','定数包模板','enabled',true),
    JSON_OBJECT('code','warehouseName','label','库房','enabled',true),
    JSON_OBJECT('code','quantity','label','包内数量','enabled',true),
    JSON_OBJECT('code','batches','label','来源批次','enabled',true),
    JSON_OBJECT('code','footer','label','页脚','enabled',true,'value','Printed by SPD')
  ),
  100, 70, 1, '默认定数包标签打印模板'
);

INSERT IGNORE INTO `sys_permission` (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`) VALUES
(0,'打印模板调整','print-template-settings',1,'/features/print-template-settings',26);

INSERT IGNORE INTO `sys_permission` (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`)
SELECT 0, '打印模板调整写操作', 'print-template-settings:write', 2, NULL, 26
  FROM DUAL
 WHERE EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'print-template-settings' AND deleted = 0);

-- 管理员与库房角色可调整打印模板
INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('print-template-settings','print-template-settings:write')
 WHERE r.role_code IN ('admin','warehouse') AND r.deleted = 0 AND p.deleted = 0;
