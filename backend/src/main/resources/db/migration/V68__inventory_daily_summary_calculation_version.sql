ALTER TABLE `inventory_daily_summary`
  ADD COLUMN `calculation_version` SMALLINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT '日报计算口径版本，用于触发历史快照顺序回算' AFTER `unit_price`,
  ADD KEY `idx_inventory_daily_summary_version_date` (`calculation_version`, `business_date`);
