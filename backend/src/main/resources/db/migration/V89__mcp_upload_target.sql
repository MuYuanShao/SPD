ALTER TABLE mcp_upload_state ADD COLUMN target_key VARCHAR(600) NOT NULL DEFAULT '' COMMENT '上传目标接口实例身份';
