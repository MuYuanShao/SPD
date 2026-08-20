ALTER TABLE `shortage_replenishment_task`
  ADD COLUMN `period_days` INT NOT NULL DEFAULT 7 COMMENT '补货周期天数' AFTER `replenish_qty`,
  ADD COLUMN `period_issue_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '周期出库数量' AFTER `period_days`,
  ADD COLUMN `avg_daily_issue_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '日均出库数量' AFTER `period_issue_qty`,
  ADD COLUMN `formula_replenish_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '公式建议补货数量' AFTER `avg_daily_issue_qty`,
  ADD COLUMN `manual_adjusted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否人工调整' AFTER `formula_replenish_qty`,
  ADD COLUMN `formula_text` VARCHAR(255) DEFAULT NULL COMMENT '补货公式说明' AFTER `manual_adjusted`;
