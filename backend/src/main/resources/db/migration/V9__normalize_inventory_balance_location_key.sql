CREATE TEMPORARY TABLE tmp_inventory_balance_merge AS
SELECT MIN(balance_id) AS keep_balance_id,
       warehouse_id,
       COALESCE(location_id, 0) AS location_key,
       product_id,
       batch_id,
       SUM(available_qty) AS available_qty,
       SUM(locked_qty) AS locked_qty,
       SUM(in_transit_qty) AS in_transit_qty,
       SUM(isolated_qty) AS isolated_qty,
       MAX(last_event_id) AS last_event_id
  FROM inventory_balance
 GROUP BY warehouse_id, COALESCE(location_id, 0), product_id, batch_id;

UPDATE inventory_balance bal
JOIN tmp_inventory_balance_merge merged ON merged.keep_balance_id = bal.balance_id
   SET bal.available_qty = merged.available_qty,
       bal.locked_qty = merged.locked_qty,
       bal.in_transit_qty = merged.in_transit_qty,
       bal.isolated_qty = merged.isolated_qty,
       bal.last_event_id = merged.last_event_id;

DELETE bal
  FROM inventory_balance bal
  JOIN tmp_inventory_balance_merge merged
    ON merged.warehouse_id = bal.warehouse_id
   AND merged.location_key = COALESCE(bal.location_id, 0)
   AND merged.product_id = bal.product_id
   AND merged.batch_id = bal.batch_id
 WHERE bal.balance_id <> merged.keep_balance_id;

DROP TEMPORARY TABLE tmp_inventory_balance_merge;

ALTER TABLE inventory_balance
  ADD COLUMN location_key BIGINT UNSIGNED
    GENERATED ALWAYS AS (COALESCE(location_id, 0)) STORED
    AFTER location_id;

ALTER TABLE inventory_balance
  ADD UNIQUE KEY uk_inventory_balance_location_key (warehouse_id, location_key, product_id, batch_id);
