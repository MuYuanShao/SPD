ALTER TABLE department_consumption
  ADD COLUMN warehouse_id BIGINT UNSIGNED NULL COMMENT '实际消耗库房' AFTER dept_id,
  ADD KEY idx_consumption_warehouse (warehouse_id);

ALTER TABLE spd_delivery_order
  ADD COLUMN destination_warehouse_id BIGINT UNSIGNED NULL COMMENT '科室目标库房' AFTER warehouse_name,
  ADD KEY idx_delivery_destination_warehouse (destination_warehouse_id);

ALTER TABLE settlement_bill_item
  ADD UNIQUE KEY uk_settlement_source_item (source_biz_type, source_biz_id);

CREATE TABLE IF NOT EXISTS purchase_plan_demand (
  plan_id BIGINT UNSIGNED NOT NULL,
  demand_id BIGINT UNSIGNED NOT NULL,
  allocated_quantity DECIMAL(18,4) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (plan_id, demand_id),
  UNIQUE KEY uk_purchase_plan_demand (demand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购计划来源需求关系';
