CREATE TABLE mcp_license_retirement (
  application_no VARCHAR(50) NOT NULL COMMENT '承载替换或撤销动作的待审批申请',
  source_attachment_id BIGINT UNSIGNED NOT NULL COMMENT '需在审批后停用的原来源附件',
  source_product_id BIGINT NOT NULL COMMENT 'MCP商品身份',
  license_code VARCHAR(80) NOT NULL COMMENT 'MCP证照编号',
  PRIMARY KEY (application_no,source_attachment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP证照替换撤销的待审批动作';
