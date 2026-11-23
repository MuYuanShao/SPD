CREATE TABLE stock_return_order (
  return_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  return_no VARCHAR(50) NOT NULL,
  return_kind VARCHAR(24) NOT NULL,
  source_warehouse_id BIGINT UNSIGNED NOT NULL,
  destination_warehouse_id BIGINT UNSIGNED NULL,
  supplier_id BIGINT UNSIGNED NULL,
  dept_id BIGINT UNSIGNED NULL,
  created_by BIGINT UNSIGNED NULL,
  request_key CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(24) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirm_time DATETIME NULL,
  PRIMARY KEY (return_id),
  UNIQUE KEY uk_stock_return_no (return_no),
  UNIQUE KEY uk_stock_return_request (request_key),
  KEY idx_stock_return_queue (return_kind,status,return_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_return_item (
  item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  return_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  batch_id BIGINT UNSIGNED NOT NULL,
  balance_id BIGINT UNSIGNED NULL,
  trace_code_id BIGINT UNSIGNED NULL,
  label_id BIGINT UNSIGNED NULL,
  quantity DECIMAL(18,4) NOT NULL,
  PRIMARY KEY (item_id),
  KEY idx_stock_return_item_order (return_id,item_id),
  FOREIGN KEY (return_id) REFERENCES stock_return_order(return_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission (perm_name,perm_code,perm_type,path)
VALUES ('退货管理','stock-return',1,'/features/stock-return'),
       ('退货登记及供应商退货确认','stock-return:write',2,NULL);
