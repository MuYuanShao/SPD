-- Permanent sequence storage. Runtime services must never execute DDL.
CREATE TABLE IF NOT EXISTS sys_sequence (
  seq_key VARCHAR(100) NOT NULL,
  seq_value BIGINT UNSIGNED NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (seq_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务永久序列';

-- Immutable approval route resolved when a catalog application is submitted.
CREATE TABLE IF NOT EXISTS pending_product_approval_route_step (
  route_step_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  application_id BIGINT UNSIGNED NOT NULL,
  approval_round INT UNSIGNED NOT NULL,
  route_order INT UNSIGNED NOT NULL,
  feature_code VARCHAR(80) NOT NULL,
  node_code VARCHAR(80) NOT NULL,
  flow_id BIGINT UNSIGNED NOT NULL,
  source_step_id BIGINT UNSIGNED NOT NULL,
  source_step_order INT UNSIGNED NOT NULL,
  step_name VARCHAR(100) NOT NULL,
  approver_type VARCHAR(30) NOT NULL,
  role_id BIGINT UNSIGNED NULL,
  user_id BIGINT UNSIGNED NULL,
  dept_id BIGINT UNSIGNED NULL,
  min_approvals INT UNSIGNED NOT NULL DEFAULT 1,
  allow_self_approve TINYINT NOT NULL DEFAULT 0,
  data_scope TINYINT NOT NULL DEFAULT 1,
  route_status VARCHAR(20) NOT NULL DEFAULT 'waiting',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  complete_time DATETIME NULL,
  PRIMARY KEY (route_step_id),
  UNIQUE KEY uk_pending_route_order (application_id, approval_round, route_order),
  KEY idx_pending_route_current (application_id, approval_round, route_status, route_order),
  KEY idx_pending_route_assignee (route_status, approver_type, role_id, user_id, dept_id),
  CONSTRAINT fk_pending_route_application FOREIGN KEY (application_id)
    REFERENCES pending_product_application (application_id) ON DELETE RESTRICT,
  CONSTRAINT fk_pending_route_flow FOREIGN KEY (flow_id)
    REFERENCES approval_flow (flow_id) ON DELETE RESTRICT,
  CONSTRAINT fk_pending_route_source_step FOREIGN KEY (source_step_id)
    REFERENCES approval_flow_step (step_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目录申请不可变审批路由快照';
