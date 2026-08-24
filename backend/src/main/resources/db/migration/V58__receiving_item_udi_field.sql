-- 验收明细独立记录 UDI：唯一码查询中的 UDI 取验收录入的 UDI 字段，不再与唯一码共用同一值。
ALTER TABLE `receiving_order_item`
  ADD COLUMN `udi_code` VARCHAR(120) DEFAULT NULL COMMENT '验收录入UDI（独立于唯一码）' AFTER `production_batch_no`;

ALTER TABLE `udi_trace_code`
  MODIFY `udi_code` VARCHAR(120) DEFAULT NULL COMMENT 'UDI code（取验收录入UDI，独立于唯一码）';
