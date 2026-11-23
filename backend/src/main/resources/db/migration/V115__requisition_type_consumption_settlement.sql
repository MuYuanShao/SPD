ALTER TABLE spd_delivery_order
  ADD COLUMN requisition_type VARCHAR(100) NULL COMMENT '来源申领类型';

UPDATE spd_delivery_order delivery
JOIN department_requisition requisition ON requisition.requisition_no = delivery.requisition_no
SET delivery.requisition_type = requisition.requisition_type;

ALTER TABLE department_consumption_item
  ADD COLUMN requisition_type VARCHAR(100) NULL COMMENT '来源申领类型';

ALTER TABLE settlement_bill_item
  ADD COLUMN requisition_type VARCHAR(100) NULL COMMENT '来源申领类型';
