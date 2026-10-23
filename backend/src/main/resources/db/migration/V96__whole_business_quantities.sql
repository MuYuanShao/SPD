-- Physical quantities must be whole numbers. Preserve decimal price/amount precision.
-- CHECK validation deliberately fails on legacy fractions; never silently round business data.

ALTER TABLE `inventory_balance`
  ADD CONSTRAINT `ck_inventory_balance_whole` CHECK (`available_qty` = TRUNCATE(`available_qty`, 0) AND `locked_qty` = TRUNCATE(`locked_qty`, 0) AND `in_transit_qty` = TRUNCATE(`in_transit_qty`, 0) AND `isolated_qty` = TRUNCATE(`isolated_qty`, 0));

ALTER TABLE `quota_package_label`
  ADD CONSTRAINT `ck_quota_package_label_whole` CHECK (`package_quantity` = TRUNCATE(`package_quantity`, 0));

ALTER TABLE `quota_package_label_source`
  ADD CONSTRAINT `ck_quota_package_label_source_whole` CHECK (`source_qty` = TRUNCATE(`source_qty`, 0));

ALTER TABLE `quota_package_template_item`
  ADD CONSTRAINT `ck_quota_package_template_item_whole` CHECK (`quantity` = TRUNCATE(`quantity`, 0));

ALTER TABLE `quota_safety_stock`
  ADD CONSTRAINT `ck_quota_safety_stock_whole` CHECK (`min_qty` = TRUNCATE(`min_qty`, 0) AND `max_qty` = TRUNCATE(`max_qty`, 0));

ALTER TABLE `purchase_order_item`
  ADD CONSTRAINT `ck_purchase_order_item_whole` CHECK (`quantity` = TRUNCATE(`quantity`, 0) AND `received_quantity` = TRUNCATE(`received_quantity`, 0));

ALTER TABLE `receiving_order_item`
  ADD CONSTRAINT `ck_receiving_order_item_whole` CHECK (`quantity` = TRUNCATE(`quantity`, 0) AND `qualified_quantity` = TRUNCATE(`qualified_quantity`, 0) AND `unqualified_quantity` = TRUNCATE(`unqualified_quantity`, 0));

ALTER TABLE `department_requisition_item`
  ADD CONSTRAINT `ck_department_requisition_item_whole` CHECK (`quantity` = TRUNCATE(`quantity`, 0) AND `requested_package_count` = TRUNCATE(`requested_package_count`, 0) AND `quota_package_quantity` = TRUNCATE(`quota_package_quantity`, 0) AND `picked_quantity` = TRUNCATE(`picked_quantity`, 0));

ALTER TABLE `department_consumption_item`
  ADD CONSTRAINT `ck_department_consumption_item_whole` CHECK (`quantity` = TRUNCATE(`quantity`, 0));

ALTER TABLE `settlement_bill_item`
  ADD CONSTRAINT `ck_settlement_bill_item_whole` CHECK (`quantity` = TRUNCATE(`quantity`, 0));

ALTER TABLE `product`
  ADD CONSTRAINT `ck_product_whole` CHECK (`min_purchase_qty` = TRUNCATE(`min_purchase_qty`, 0) AND `conversion_rate` = TRUNCATE(`conversion_rate`, 0) AND `purchase_package_qty` = TRUNCATE(`purchase_package_qty`, 0));

ALTER TABLE `quota_packing_task`
  ADD CONSTRAINT `ck_quota_packing_task_whole` CHECK (`package_count` = TRUNCATE(`package_count`, 0) AND `package_quantity` = TRUNCATE(`package_quantity`, 0) AND `planned_loose_qty` = TRUNCATE(`planned_loose_qty`, 0) AND `reserved_loose_qty` = TRUNCATE(`reserved_loose_qty`, 0));

ALTER TABLE `quota_packing_task_reservation`
  ADD CONSTRAINT `ck_quota_packing_task_reservation_whole` CHECK (`reserved_qty` = TRUNCATE(`reserved_qty`, 0));

ALTER TABLE `spd_delivery_order`
  ADD CONSTRAINT `ck_spd_delivery_order_whole` CHECK (`quantity` = TRUNCATE(`quantity`, 0));

ALTER TABLE `spd_delivery_package_binding`
  ADD CONSTRAINT `ck_spd_delivery_package_binding_whole` CHECK (`package_quantity` = TRUNCATE(`package_quantity`, 0));

ALTER TABLE `spd_delivery_quota_package`
  ADD CONSTRAINT `ck_spd_delivery_quota_package_whole` CHECK (`package_quantity` = TRUNCATE(`package_quantity`, 0));

ALTER TABLE `spd_delivery_batch`
  ADD CONSTRAINT `ck_spd_delivery_batch_whole` CHECK (`quantity` = TRUNCATE(`quantity`, 0));

ALTER TABLE `inventory_event`
  ADD CONSTRAINT `ck_inventory_event_whole` CHECK (`qty_change` = TRUNCATE(`qty_change`, 0) AND `qty_after` = TRUNCATE(`qty_after`, 0));

ALTER TABLE `inventory_event_trace_code`
  ADD CONSTRAINT `ck_inventory_event_trace_code_whole` CHECK (`linked_quantity` = TRUNCATE(`linked_quantity`, 0));

ALTER TABLE `high_value_charge`
  ADD CONSTRAINT `ck_high_value_charge_whole` CHECK (`quantity` = TRUNCATE(`quantity`, 0));

ALTER TABLE `inventory_stocktaking_item`
  ADD CONSTRAINT `ck_inventory_stocktaking_item_whole` CHECK (`system_qty` = TRUNCATE(`system_qty`, 0) AND `actual_qty` = TRUNCATE(`actual_qty`, 0) AND `diff_qty` = TRUNCATE(`diff_qty`, 0));

ALTER TABLE `shortage_replenishment_task`
  ADD CONSTRAINT `ck_shortage_replenishment_task_whole` CHECK (`min_qty` = TRUNCATE(`min_qty`, 0) AND `current_qty` = TRUNCATE(`current_qty`, 0) AND `replenish_qty` = TRUNCATE(`replenish_qty`, 0));

ALTER TABLE `batch_price_adjustment`
  ADD CONSTRAINT `ck_batch_price_adjustment_whole` CHECK (`affected_qty` = TRUNCATE(`affected_qty`, 0));

