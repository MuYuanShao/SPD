-- 打包任务预占明细记录验收单来源，支持按验收单号分配散货库存
ALTER TABLE `quota_packing_task_reservation`
  ADD COLUMN `receiving_no` VARCHAR(50) NULL COMMENT '来源验收单号' AFTER `batch_id`,
  ADD COLUMN `receiving_item_id` BIGINT UNSIGNED NULL COMMENT '来源验收明细ID' AFTER `receiving_no`;
