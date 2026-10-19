
-- V88: mcp phase1 integration
-- (Triggers removed: V88 cannot create triggers because the application user lacks SUPER privilege
--  and the server has binary logging enabled. update_time sync for purchase_order_item is now
--  handled at the service layer.)

CREATE TABLE IF NOT EXISTS mcp_inbound_product (
  source_product_id BIGINT NOT NULL PRIMARY KEY COMMENT 'MCP-UI商品ID',
  product_code VARCHAR(50) NOT NULL UNIQUE COMMENT 'SPD商品编码',
  application_no VARCHAR(50) NOT NULL COMMENT '待审批目录申请编号',
  source_updated_at VARCHAR(40) NOT NULL COMMENT '来源版本时间',
  license_updated_at VARCHAR(40) NOT NULL DEFAULT '' COMMENT '证照来源版本时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP-UI待审批目录映射';

CREATE TABLE IF NOT EXISTS mcp_receive_result (
  sync_id VARCHAR(36) PRIMARY KEY COMMENT '幂等同步编号',
  request_hash VARCHAR(64) NOT NULL COMMENT '请求摘要',
  result_json JSON NOT NULL COMMENT '处理结果',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP商品下发幂等回执';

CREATE TABLE IF NOT EXISTS mcp_pending_license (
  source_product_id BIGINT NOT NULL COMMENT 'MCP-UI商品ID',
  license_code VARCHAR(80) NOT NULL COMMENT '来源证照编号',
  application_no VARCHAR(50) NOT NULL COMMENT '待审批目录申请编号',
  attachment_id BIGINT NULL COMMENT 'SPD审批附件ID',
  metadata JSON NOT NULL COMMENT '证照类型有效期状态',
  PRIMARY KEY (source_product_id, license_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP下发待审批证照';

CREATE TABLE IF NOT EXISTS mcp_upload_state (
  kind VARCHAR(30) NOT NULL COMMENT '对象类型',
  source_code VARCHAR(100) NOT NULL COMMENT '来源业务编码',
  payload_hash VARCHAR(64) NULL COMMENT '已成功上传数据摘要',
  state VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '同步状态',
  message VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '同步结果',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY(kind,source_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP资料订单增量上传状态';

-- ALTER statements for update_time precision
ALTER TABLE supplier MODIFY update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
ALTER TABLE manufacturer MODIFY update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
ALTER TABLE purchase_order MODIFY update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
