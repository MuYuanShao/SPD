CREATE TABLE mcp_license_intent (
  application_no VARCHAR(50) NOT NULL,
  source_product_id BIGINT NOT NULL,
  source_license_id VARCHAR(100) NOT NULL,
  product_code VARCHAR(50) NOT NULL,
  license_code VARCHAR(80) NOT NULL,
  attachment_id BIGINT UNSIGNED NULL,
  metadata JSON NOT NULL,
  source_updated_at VARCHAR(40) NOT NULL,
  PRIMARY KEY(application_no,source_product_id,source_license_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按稳定证照身份保存的待审批版本';
CREATE TABLE mcp_license_approved (
  source_product_id BIGINT NOT NULL,
  source_license_id VARCHAR(100) NOT NULL,
  product_code VARCHAR(50) NOT NULL,
  license_code VARCHAR(80) NOT NULL,
  attachment_id BIGINT UNSIGNED NULL,
  metadata JSON NOT NULL,
  application_no VARCHAR(50) NOT NULL,
  source_updated_at VARCHAR(40) NOT NULL,
  PRIMARY KEY(source_product_id,source_license_id),
  KEY idx_mcp_approved_product(product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仅审批通过后更新的当前证照版本';

INSERT IGNORE INTO mcp_license_approved
SELECT l.source_product_id,CONCAT('legacy:',l.license_code),mp.product_code,l.license_code,a.source_attachment_id,
  JSON_SET(l.metadata,'$.status',CASE WHEN SUBSTRING_INDEX(a.description,' / ',-1) IN ('active','pending','expired','revoked') THEN SUBSTRING_INDEX(a.description,' / ',-1) ELSE 'pending' END,
    '$.expiryDate',DATE_FORMAT(a.valid_date,'%Y-%m-%d'),'$.issueDate',NULL),
  l.application_no,'1970-01-01T00:00:00Z'
FROM mcp_pending_license l JOIN mcp_inbound_product mp ON mp.source_product_id=l.source_product_id
JOIN product p ON p.product_code=mp.product_code
JOIN sys_attachment a ON a.biz_type='product' AND a.biz_id=p.product_id AND a.deleted=0
WHERE a.source_attachment_id=l.attachment_id OR EXISTS (
  SELECT 1 FROM mcp_license_retirement r WHERE r.source_product_id=l.source_product_id AND r.license_code=l.license_code AND r.source_attachment_id=a.source_attachment_id
);
