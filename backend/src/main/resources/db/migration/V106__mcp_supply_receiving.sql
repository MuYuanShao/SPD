ALTER TABLE receiving_order
  ADD COLUMN source_system VARCHAR(30) NULL,
  ADD COLUMN external_supply_no VARCHAR(100) NULL;

CREATE TABLE mcp_supply_receipt (
  message_id CHAR(36) NOT NULL PRIMARY KEY,
  organization VARCHAR(80) NOT NULL,
  supplier_code VARCHAR(100) NOT NULL,
  supply_no VARCHAR(100) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  purchase_order_id BIGINT UNSIGNED NOT NULL,
  receiving_order_id BIGINT UNSIGNED NOT NULL,
  payload JSON NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_mcp_supply_document(organization,supplier_code,supply_no,purchase_order_id),
  UNIQUE KEY uk_mcp_supply_receiving(receiving_order_id),
  CONSTRAINT fk_mcp_supply_receiving FOREIGN KEY(receiving_order_id) REFERENCES receiving_order(receiving_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO sys_permission(parent_id,perm_name,perm_code,perm_type,path,sort_order)
VALUES(0,'MCP供货回传接收','mcp-phase3:supply',2,NULL,406);
INSERT IGNORE INTO sys_role_perm(role_id,perm_id)
SELECT r.role_id,p.perm_id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('admin','ROLE_ADMIN') AND r.deleted=0 AND p.perm_code='mcp-phase3:supply';
