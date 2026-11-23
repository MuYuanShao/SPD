ALTER TABLE department_requisition
  MODIFY COLUMN requisition_type VARCHAR(100) NOT NULL COMMENT '申领类型';

INSERT INTO sys_field_option (field_key, field_label, option_value, option_label, sort_order)
VALUES ('requisition_type', '申领类型', 'regular_requisition', '常规申领', 10)
ON DUPLICATE KEY UPDATE option_id = option_id;
