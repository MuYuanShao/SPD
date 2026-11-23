-- Links each new loose consumption line to its actual deduction and preserves known types on reversal receipts.
ALTER TABLE department_consumption_item
  ADD COLUMN inventory_issue_event_id BIGINT UNSIGNED NULL COMMENT '散货扣减库存事件',
  ADD CONSTRAINT fk_consumption_item_inventory_issue
    FOREIGN KEY (inventory_issue_event_id) REFERENCES inventory_event (event_id);

ALTER TABLE inventory_event
  ADD COLUMN requisition_type VARCHAR(100) NULL COMMENT '反消耗恢复库存的来源申领类型';
