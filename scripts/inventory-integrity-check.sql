-- Must return zero rows before the maintenance-window constraint migration is applied.
SELECT 'negative_inventory_balance' AS issue_type, balance_id AS entity_id
  FROM inventory_balance
 WHERE available_qty < 0 OR locked_qty < 0 OR in_transit_qty < 0 OR isolated_qty < 0
UNION ALL
SELECT 'orphan_inventory_batch_product', ib.batch_id
  FROM inventory_batch ib LEFT JOIN product p ON p.product_id = ib.product_id
 WHERE p.product_id IS NULL
UNION ALL
SELECT 'orphan_inventory_balance_warehouse', bal.balance_id
  FROM inventory_balance bal LEFT JOIN warehouse w ON w.warehouse_id = bal.warehouse_id
 WHERE w.warehouse_id IS NULL
UNION ALL
SELECT 'orphan_inventory_balance_batch', bal.balance_id
  FROM inventory_balance bal LEFT JOIN inventory_batch ib ON ib.batch_id = bal.batch_id
 WHERE ib.batch_id IS NULL
UNION ALL
SELECT 'invalid_inventory_location', bal.balance_id
  FROM inventory_balance bal
  JOIN warehouse_location wl ON wl.location_id = bal.location_id
 WHERE wl.warehouse_id <> bal.warehouse_id OR wl.deleted = 1
UNION ALL
SELECT 'duplicate_settlement_source', MIN(item_id)
  FROM settlement_bill_item
 GROUP BY source_biz_type, source_biz_id
HAVING COUNT(*) > 1;
