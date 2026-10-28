-- Persist non-catalog approval progress independently of mutable flow configuration.
CREATE TABLE business_approval_run (
  document_key VARCHAR(220) NOT NULL PRIMARY KEY,
  current_order INT NOT NULL,
  flow_data_scope INT NOT NULL DEFAULT 1,
  status VARCHAR(20) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE business_approval_step (
  document_key VARCHAR(220) NOT NULL,
  step_order INT NOT NULL,
  approver_type VARCHAR(30) NOT NULL,
  role_code VARCHAR(100) NULL,
  user_id BIGINT UNSIGNED NULL,
  dept_id BIGINT UNSIGNED NULL,
  min_approvals INT NOT NULL DEFAULT 1,
  allow_self_approve TINYINT NOT NULL DEFAULT 0,
  data_scope INT NOT NULL DEFAULT 1,
  PRIMARY KEY (document_key, step_order),
  CONSTRAINT ck_business_approval_quorum CHECK (min_approvals > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE business_approval_vote (
  document_key VARCHAR(220) NOT NULL,
  step_order INT NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (document_key, step_order, operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE inventory_stocktaking_item ADD COLUMN snapshot_event_id BIGINT UNSIGNED NULL COMMENT '盘点快照最后库存事件，历史草稿需重建';
UPDATE high_value_charge SET source_system = 'AIMS' WHERE source_system IS NULL OR TRIM(source_system) = '';
ALTER TABLE high_value_charge DROP INDEX uk_high_value_external_charge,
  ADD UNIQUE KEY uk_high_value_source_charge (source_system, external_charge_no);
