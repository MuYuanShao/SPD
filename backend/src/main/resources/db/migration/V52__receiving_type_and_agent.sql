-- 收货验收新增收货界面字段：收货类型与是否代理商
ALTER TABLE `receiving_order`
  ADD COLUMN `receiving_type` VARCHAR(30) NULL COMMENT '收货类型' AFTER `receiving_status`,
  ADD COLUMN `is_agent` TINYINT NOT NULL DEFAULT 0 COMMENT '是否代理商' AFTER `receiving_type`;

-- 收货类型下拉选项（字段管理维护表）
INSERT INTO `sys_field_option` (`field_key`, `field_label`, `option_value`, `option_label`, `sort_order`) VALUES
('receiving_type', '收货类型', 'normal', '正常收货', 10),
('receiving_type', '收货类型', 'return', '退货收货', 20),
('receiving_type', '收货类型', 'exchange', '换货收货', 30),
('receiving_type', '收货类型', 'agent', '代理商直送', 40);
