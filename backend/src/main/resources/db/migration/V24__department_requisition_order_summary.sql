ALTER TABLE `department_requisition`
  ADD COLUMN `warehouse_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '申领库房' AFTER `dept_id`,
  ADD KEY `idx_requisition_warehouse` (`warehouse_id`);

ALTER TABLE `department_requisition_item`
  ADD COLUMN `unit_price` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '申请单价' AFTER `unit`,
  ADD COLUMN `amount` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '申请金额' AFTER `unit_price`;

UPDATE `department_requisition_item` dri
  JOIN `product` p ON p.product_id = dri.product_id
   SET dri.unit_price = p.purchase_price,
       dri.amount = ROUND(dri.quantity * p.purchase_price, 4)
 WHERE dri.unit_price = 0
   AND dri.amount = 0;
