-- Fail before changing the schema if the migration account cannot create triggers.
SET @ledger_trigger_privilege = (
  SELECT COUNT(*) FROM (
    SELECT GRANTEE, PRIVILEGE_TYPE FROM information_schema.USER_PRIVILEGES
      WHERE PRIVILEGE_TYPE IN ('TRIGGER', 'SUPER')
    UNION ALL
    SELECT GRANTEE, PRIVILEGE_TYPE FROM information_schema.SCHEMA_PRIVILEGES
      WHERE TABLE_SCHEMA = DATABASE() AND PRIVILEGE_TYPE = 'TRIGGER'
  ) grants_for_ledger WHERE REPLACE(GRANTEE, CHAR(39), '') = CURRENT_USER()
);
SET @ledger_super_privilege = (
  SELECT COUNT(*) FROM information_schema.USER_PRIVILEGES
   WHERE REPLACE(GRANTEE, CHAR(39), '') = CURRENT_USER() AND PRIVILEGE_TYPE = 'SUPER'
);
SET @ledger_preflight_sql = IF(
  @ledger_trigger_privilege > 0 AND
  (@@GLOBAL.log_bin = 0 OR @@GLOBAL.log_bin_trust_function_creators = 1 OR @ledger_super_privilege > 0),
  'SELECT 1',
  'SELECT * FROM V78_requires_TRIGGER_and_binlog_creator_privileges');
PREPARE ledger_preflight FROM @ledger_preflight_sql;
EXECUTE ledger_preflight;
DEALLOCATE PREPARE ledger_preflight;

-- Install unconditional guards before removing the V77 guards.
CREATE TRIGGER trg_ledger_event_no_update BEFORE UPDATE ON inventory_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory events are immutable';
CREATE TRIGGER trg_ledger_event_no_delete BEFORE DELETE ON inventory_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory events are immutable';
CREATE TRIGGER trg_ledger_trace_no_update BEFORE UPDATE ON inventory_event_trace_code
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory event trace links are immutable';
CREATE TRIGGER trg_ledger_trace_no_delete BEFORE DELETE ON inventory_event_trace_code
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'inventory event trace links are immutable';
DROP TRIGGER trg_inventory_event_immutable_update;
DROP TRIGGER trg_inventory_event_immutable_delete;
DROP TRIGGER trg_inventory_event_trace_immutable_update;
DROP TRIGGER trg_inventory_event_trace_immutable_delete;

CREATE TABLE spd_delivery_batch (
  delivery_id BIGINT UNSIGNED NOT NULL,
  source_event_id BIGINT UNSIGNED NOT NULL,
  source_warehouse_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  batch_id BIGINT UNSIGNED NOT NULL,
  quantity DECIMAL(18,4) NOT NULL,
  PRIMARY KEY (delivery_id, source_event_id),
  UNIQUE KEY uk_delivery_batch_source_event (source_event_id),
  CONSTRAINT fk_delivery_batch_event FOREIGN KEY (source_event_id) REFERENCES inventory_event(event_id),
  CONSTRAINT fk_delivery_batch_delivery FOREIGN KEY (delivery_id) REFERENCES spd_delivery_order(delivery_id),
  CONSTRAINT ck_delivery_batch_positive CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Backfill only pending deliveries whose complete event set agrees with the saved requisition.
INSERT INTO spd_delivery_batch
  (delivery_id, source_event_id, source_warehouse_id, product_id, batch_id, quantity)
SELECT d.delivery_id, e.event_id, e.warehouse_id, e.product_id, e.batch_id, -e.qty_change
FROM spd_delivery_order d
JOIN department_requisition r ON r.requisition_no = d.requisition_no
JOIN department_requisition_item ri ON ri.item_id = d.requisition_item_id AND ri.requisition_id = r.requisition_id
JOIN inventory_event e ON e.source_biz_type = 'spd_delivery_order' AND e.source_biz_id = d.delivery_id
WHERE d.status = 'picked' AND d.delivery_type = 'loose'
  AND e.event_type = 'delivery_loose_out' AND e.qty_change < 0
  AND e.warehouse_id = r.source_warehouse_id AND e.product_id = ri.product_id
  AND d.quantity = (SELECT -SUM(x.qty_change) FROM inventory_event x
     WHERE x.source_biz_type = 'spd_delivery_order' AND x.source_biz_id = d.delivery_id)
  AND NOT EXISTS (SELECT 1 FROM inventory_event x
     WHERE x.source_biz_type = 'spd_delivery_order' AND x.source_biz_id = d.delivery_id
       AND (x.event_type <> 'delivery_loose_out' OR x.qty_change >= 0
            OR x.warehouse_id <> r.source_warehouse_id OR x.product_id <> ri.product_id));
