CREATE TABLE mobile_operation_receipt (
  user_id BIGINT UNSIGNED NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  device_id VARCHAR(128) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  task_id BIGINT UNSIGNED NOT NULL,
  dept_id BIGINT UNSIGNED NOT NULL,
  warehouse_id BIGINT UNSIGNED NOT NULL,
  payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(16) NOT NULL,
  result_json JSON NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at DATETIME(6) NULL,
  PRIMARY KEY (user_id, operation_id),
  KEY idx_mobile_receipt_task (kind, task_id),
  CONSTRAINT ck_mobile_receipt_status CHECK (status IN ('pending', 'succeeded'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
