ALTER TABLE license_document ADD COLUMN revision_no INT NOT NULL DEFAULT 1 COMMENT '当前证照版本';
CREATE TABLE license_revision (
  revision_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  license_id BIGINT UNSIGNED NOT NULL,
  revision_no INT NOT NULL,
  operation_type VARCHAR(30) NOT NULL,
  snapshot_json JSON NOT NULL,
  operator_id BIGINT UNSIGNED NULL,
  operator_name VARCHAR(100) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (revision_id),
  UNIQUE KEY uk_license_revision (license_id, revision_no),
  CONSTRAINT fk_license_revision_document FOREIGN KEY (license_id) REFERENCES license_document(license_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变证照变更与续证历史';
