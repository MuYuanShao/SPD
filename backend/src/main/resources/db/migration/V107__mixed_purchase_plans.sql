-- Preserve legacy identities; new plans use their detail rows rather than header product/supplier fields.
ALTER TABLE purchase_plan
  MODIFY supplier_id BIGINT UNSIGNED NULL COMMENT '历史单商品计划供应商，新计划使用明细配送商',
  MODIFY product_id BIGINT UNSIGNED NULL COMMENT '历史单商品计划商品，新计划使用明细',
  MODIFY planned_quantity DECIMAL(18,4) NULL COMMENT '历史单商品计划数量',
  ADD create_by BIGINT UNSIGNED NULL,
  ADD dept_id BIGINT UNSIGNED NULL;

CREATE TABLE purchase_plan_item (
  plan_item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT UNSIGNED NOT NULL,
  demand_id BIGINT UNSIGNED NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  supplier_id BIGINT UNSIGNED NOT NULL,
  quantity DECIMAL(18,4) NOT NULL,
  unit VARCHAR(30) NOT NULL,
  estimated_unit_price DECIMAL(18,4) NULL,
  price_basis VARCHAR(40) NOT NULL DEFAULT 'catalog_at_plan_creation',
  UNIQUE KEY uk_plan_item_demand(demand_id),
  KEY ix_plan_item_plan(plan_id,supplier_id),
  CONSTRAINT fk_plan_item_plan FOREIGN KEY(plan_id) REFERENCES purchase_plan(plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO purchase_plan_item(plan_item_id,plan_id,demand_id,product_id,supplier_id,quantity,unit,price_basis)
SELECT pp.plan_id,pp.plan_id,(SELECT MIN(demand_id) FROM purchase_plan_demand WHERE plan_id=pp.plan_id),
       pp.product_id,pp.supplier_id,pp.planned_quantity,p.unit,'not_recorded'
FROM purchase_plan pp JOIN product p ON p.product_id=pp.product_id;

-- Each supplier sees one stable plan projection. Legacy projection/line IDs remain unchanged.
CREATE TABLE purchase_plan_supplier (
  projection_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT UNSIGNED NOT NULL,
  supplier_id BIGINT UNSIGNED NOT NULL,
  UNIQUE KEY uk_plan_supplier(plan_id,supplier_id),
  CONSTRAINT fk_plan_supplier_plan FOREIGN KEY(plan_id) REFERENCES purchase_plan(plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO purchase_plan_supplier(projection_id,plan_id,supplier_id)
SELECT plan_id,plan_id,supplier_id FROM purchase_plan;

UPDATE purchase_plan pp SET
  create_by=(SELECT MIN(pd.create_by) FROM purchase_plan_demand d JOIN purchase_demand pd ON pd.demand_id=d.demand_id WHERE d.plan_id=pp.plan_id),
  dept_id=(SELECT MIN(pd.dept_id) FROM purchase_plan_demand d JOIN purchase_demand pd ON pd.demand_id=d.demand_id WHERE d.plan_id=pp.plan_id);

ALTER TABLE purchase_order ADD source_plan_id BIGINT UNSIGNED NULL,
  ADD UNIQUE KEY uk_order_plan_supplier(source_plan_id,supplier_id),
  ADD CONSTRAINT fk_order_source_plan FOREIGN KEY(source_plan_id) REFERENCES purchase_plan(plan_id);
UPDATE purchase_order po JOIN purchase_plan pp ON pp.converted_order_no=po.order_no SET po.source_plan_id=pp.plan_id;
ALTER TABLE purchase_order_item ADD source_plan_item_id BIGINT UNSIGNED NULL,
  ADD UNIQUE KEY uk_order_plan_item(source_plan_item_id),
  ADD CONSTRAINT fk_order_source_plan_item FOREIGN KEY(source_plan_item_id) REFERENCES purchase_plan_item(plan_item_id);
UPDATE purchase_order_item oi JOIN purchase_order po ON po.purchase_order_id=oi.purchase_order_id
  JOIN purchase_plan_item pi ON pi.plan_id=po.source_plan_id AND pi.product_id=oi.product_id
SET oi.source_plan_item_id=pi.plan_item_id
WHERE (SELECT n FROM (SELECT purchase_order_id,COUNT(*) n FROM purchase_order_item GROUP BY purchase_order_id) counts
       WHERE counts.purchase_order_id=oi.purchase_order_id)=1;
