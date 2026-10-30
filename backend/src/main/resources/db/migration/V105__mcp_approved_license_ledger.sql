ALTER TABLE license_document
  MODIFY COLUMN license_name VARCHAR(255) NOT NULL,
  ADD COLUMN source_product_id BIGINT NULL COMMENT 'MCP来源商品身份',
  ADD COLUMN source_license_id VARCHAR(100) NULL COMMENT 'MCP稳定来源证照身份',
  ADD COLUMN source_updated_at VARCHAR(40) NULL,
  ADD COLUMN source_application_no VARCHAR(50) NULL,
  ADD UNIQUE KEY uk_license_mcp_source(source_product_id,source_license_id);

-- Only already-approved facts are backfilled; pending and rejected intents are never made effective here.
INSERT INTO license_document(license_type,license_name,license_no,owner_type,owner_id,owner_code,owner_name,
  issue_date,expire_date,status,remark,deleted,source_product_id,source_license_id,source_updated_at,source_application_no)
SELECT 'product',COALESCE(NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(a.metadata,'$.licenseName')),'null'),''),a.license_code),
  a.license_code,'product',p.product_id,p.product_code,p.product_name,
  NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(a.metadata,'$.issueDate')),'null'),''),
  NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(a.metadata,'$.expiryDate')),'null'),''),
  CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(a.metadata,'$.status'))='active' THEN 1 ELSE 0 END,
  'MCP-UI来源证照，变更须经过院内目录审批',
  CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(a.metadata,'$.status'))='unlinked' THEN 1 ELSE 0 END,
  a.source_product_id,a.source_license_id,a.source_updated_at,a.application_no
FROM mcp_license_approved a JOIN product p ON p.product_code=a.product_code;

INSERT INTO sys_attachment(biz_type,biz_id,file_name,file_ext,file_type,file_size,file_path,file_url,
  category,description,valid_date,create_by,source_attachment_id)
SELECT 'license',l.license_id,s.file_name,s.file_ext,s.file_type,s.file_size,CONCAT('pending-products/',s.file_path),
  CONCAT('/pending-product-applications/attachments/',s.attachment_id,'/file'),s.category,s.description,s.valid_date,s.create_by,s.attachment_id
FROM license_document l JOIN mcp_license_approved a
  ON a.source_product_id=l.source_product_id AND a.source_license_id=l.source_license_id
JOIN sys_attachment s ON s.attachment_id=a.attachment_id AND s.biz_type='pending_product_application' AND s.deleted=0
WHERE l.deleted=0;

INSERT INTO license_revision(license_id,revision_no,operation_type,snapshot_json,operator_name)
SELECT l.license_id,l.revision_no,'mcp_import',JSON_OBJECT(
  'licenseName',l.license_name,'licenseType',l.license_type,'licenseNo',l.license_no,
  'ownerType',l.owner_type,'ownerId',l.owner_id,'ownerCode',l.owner_code,'ownerName',l.owner_name,
  'issueDate',l.issue_date,'expireDate',l.expire_date,'status',l.status,'deleted',l.deleted,'remark',l.remark,
  'sourceProductId',CAST(l.source_product_id AS CHAR),'sourceLicenseId',l.source_license_id,
  'sourceApplicationNo',l.source_application_no,'sourceUpdatedAt',l.source_updated_at,
  'attachments',COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('id',a.attachment_id,'fileName',a.file_name,'fileType',a.file_type,'size',a.file_size))
    FROM sys_attachment a WHERE a.biz_type='license' AND a.biz_id=l.license_id AND a.deleted=0),JSON_ARRAY())
),'系统迁移' FROM license_document l WHERE l.source_product_id IS NOT NULL;
