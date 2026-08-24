-- 范围盘点明细按商品汇总，允许 balance_id/batch_id 为空，盘点数量与差异待录入。
ALTER TABLE `inventory_stocktaking_item`
  MODIFY `balance_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '库存余额ID（范围盘点按商品汇总为空）',
  MODIFY `batch_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '系统批次ID（范围盘点按商品汇总为空）',
  MODIFY `actual_qty` DECIMAL(18,4) DEFAULT NULL COMMENT '实际数量',
  MODIFY `diff_qty` DECIMAL(18,4) DEFAULT NULL COMMENT '差异数量';
