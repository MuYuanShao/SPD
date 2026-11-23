ALTER TABLE recall_event
  ADD COLUMN destination_warehouse_id BIGINT UNSIGNED NULL COMMENT '召回待签收中心库ID',
  ADD COLUMN received_time DATETIME NULL COMMENT '召回签收入库时间';
