DELETE FROM inventory_balance
 WHERE available_qty = 0
   AND locked_qty = 0
   AND in_transit_qty = 0
   AND isolated_qty = 0;
