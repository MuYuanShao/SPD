-- Maintenance-window migration. Run scripts/run-inventory-integrity-check.ps1 before application startup.
DROP PROCEDURE IF EXISTS spd_add_constraint_if_missing;
DELIMITER $$
CREATE PROCEDURE spd_add_constraint_if_missing(
    IN p_table VARCHAR(64), IN p_constraint VARCHAR(64), IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE constraint_schema = DATABASE()
           AND table_name = p_table AND constraint_name = p_constraint
    ) THEN
        SET @constraint_sql = CONCAT('ALTER TABLE `', p_table, '` ADD CONSTRAINT `',
                                     p_constraint, '` ', p_definition);
        PREPARE constraint_stmt FROM @constraint_sql;
        EXECUTE constraint_stmt;
        DEALLOCATE PREPARE constraint_stmt;
    END IF;
END$$
DELIMITER ;

CALL spd_add_constraint_if_missing('inventory_batch', 'fk_inventory_batch_product',
    'FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('inventory_batch', 'fk_inventory_batch_supplier',
    'FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`supplier_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('inventory_balance', 'fk_inventory_balance_warehouse',
    'FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`warehouse_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('inventory_balance', 'fk_inventory_balance_location',
    'FOREIGN KEY (`location_id`) REFERENCES `warehouse_location` (`location_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('inventory_balance', 'fk_inventory_balance_product',
    'FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('inventory_balance', 'fk_inventory_balance_batch',
    'FOREIGN KEY (`batch_id`) REFERENCES `inventory_batch` (`batch_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('inventory_event', 'fk_inventory_event_warehouse',
    'FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`warehouse_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('inventory_event', 'fk_inventory_event_location',
    'FOREIGN KEY (`location_id`) REFERENCES `warehouse_location` (`location_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('inventory_event', 'fk_inventory_event_product',
    'FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('inventory_event', 'fk_inventory_event_batch',
    'FOREIGN KEY (`batch_id`) REFERENCES `inventory_batch` (`batch_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('warehouse_location', 'fk_warehouse_location_warehouse',
    'FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`warehouse_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('warehouse_location', 'fk_warehouse_location_product',
    'FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('settlement_bill', 'fk_settlement_bill_supplier',
    'FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`supplier_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('settlement_bill_item', 'fk_settlement_item_bill',
    'FOREIGN KEY (`settlement_id`) REFERENCES `settlement_bill` (`settlement_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('settlement_bill_item', 'fk_settlement_item_product',
    'FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
CALL spd_add_constraint_if_missing('settlement_bill_item', 'fk_settlement_item_batch',
    'FOREIGN KEY (`batch_id`) REFERENCES `inventory_batch` (`batch_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');

CALL spd_add_constraint_if_missing('inventory_balance', 'ck_inventory_balance_nonnegative',
    'CHECK (`available_qty` >= 0 AND `locked_qty` >= 0 AND `in_transit_qty` >= 0 AND `isolated_qty` >= 0)');
CALL spd_add_constraint_if_missing('inventory_event', 'ck_inventory_event_nonnegative_after',
    'CHECK (`qty_after` >= 0)');
CALL spd_add_constraint_if_missing('quota_package_template_item', 'ck_quota_template_quantity_positive',
    'CHECK (`quantity` > 0)');
CALL spd_add_constraint_if_missing('settlement_bill_item', 'ck_settlement_item_values',
    'CHECK (`quantity` > 0 AND `unit_price` >= 0 AND `amount` >= 0)');

DROP PROCEDURE spd_add_constraint_if_missing;
