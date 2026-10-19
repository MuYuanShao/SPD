-- V90: mcp receiving links
-- (Triggers removed: see V88 for rationale. update_time sync for receiving_order_item
--  is now handled at the service layer.)

ALTER TABLE receiving_order_item ADD COLUMN purchase_order_item_id BIGINT UNSIGNED NULL COMMENT '明确关联的采购订单明细ID，历史数据仅在唯一匹配时自动关联';
ALTER TABLE receiving_order MODIFY update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
