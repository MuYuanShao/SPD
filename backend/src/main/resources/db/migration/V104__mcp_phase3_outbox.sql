CREATE TABLE mcp_phase3_document (
  document_type VARCHAR(30) NOT NULL,
  source_id BIGINT NOT NULL,
  revision BIGINT NOT NULL DEFAULT 0,
  source_hash CHAR(64) NULL,
  published TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY(document_type,source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO sys_permission(parent_id,perm_name,perm_code,perm_type,path,sort_order)
VALUES(0,'MCP第三期协同反馈接收','mcp-phase3:feedback',2,NULL,405);
INSERT IGNORE INTO sys_role_perm(role_id,perm_id)
SELECT r.role_id,p.perm_id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('admin','ROLE_ADMIN') AND r.deleted=0 AND p.perm_code='mcp-phase3:feedback';

CREATE TABLE mcp_phase3_outbox (
  message_id CHAR(36) NOT NULL PRIMARY KEY,
  document_type VARCHAR(30) NOT NULL,
  source_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  operation VARCHAR(10) NOT NULL,
  source_updated_at VARCHAR(40) NOT NULL,
  source_payload JSON NOT NULL,
  wire_payload JSON NULL,
  payload_hash CHAR(64) NULL,
  target_key VARCHAR(700) NULL,
  state VARCHAR(20) NOT NULL DEFAULT 'pending',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  lease_id CHAR(36) NULL,
  lease_until DATETIME(6) NULL,
  message VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_mcp_phase3_version(document_type,source_id,revision),
  KEY ix_mcp_phase3_delivery(state,next_attempt,lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mcp_phase3_feedback (
  message_id CHAR(36) NOT NULL PRIMARY KEY,
  payload_hash CHAR(64) NOT NULL,
  settlement_id BIGINT NOT NULL,
  settlement_revision BIGINT NOT NULL,
  supplier_code VARCHAR(100) NOT NULL,
  feedback_type VARCHAR(20) NOT NULL,
  payload JSON NOT NULL,
  received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY ix_mcp_phase3_feedback(settlement_id,received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
