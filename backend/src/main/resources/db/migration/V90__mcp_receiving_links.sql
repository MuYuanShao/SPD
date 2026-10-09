ALTER TABLE receiving_order_item ADD COLUMN purchase_order_item_id BIGINT UNSIGNED NULL COMMENT '明确关联的采购订单明细ID，历史数据仅在唯一匹配时自动关联';
ALTER TABLE receiving_order MODIFY update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
CREATE TRIGGER mcp_receiving_item_insert AFTER INSERT ON receiving_order_item FOR EACH ROW
  UPDATE receiving_order SET update_time=CURRENT_TIMESTAMP(6) WHERE receiving_order_id=NEW.receiving_order_id;
CREATE TRIGGER mcp_receiving_item_update AFTER UPDATE ON receiving_order_item FOR EACH ROW
  UPDATE receiving_order SET update_time=CURRENT_TIMESTAMP(6) WHERE receiving_order_id=NEW.receiving_order_id;
CREATE TRIGGER mcp_receiving_item_delete AFTER DELETE ON receiving_order_item FOR EACH ROW
  UPDATE receiving_order SET update_time=CURRENT_TIMESTAMP(6) WHERE receiving_order_id=OLD.receiving_order_id;
