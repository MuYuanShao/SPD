ALTER TABLE product
  ADD COLUMN catalog_price_version BIGINT NOT NULL DEFAULT 0 COMMENT '目录价格版本';

ALTER TABLE quota_package_label
  ADD COLUMN settlement_unit_price DECIMAL(18,6) NULL COMMENT '未结算套包当前基本单位计价；NULL保留来源成本计价',
  ADD COLUMN catalog_price_version BIGINT NULL COMMENT '当前计价对应的目录价格版本',
  ADD KEY idx_quota_label_product_pricing (product_id, label_id);

CREATE TABLE catalog_price_change (
  change_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  application_no VARCHAR(64) NOT NULL COMMENT '最终通过的目录审批单',
  product_id BIGINT UNSIGNED NOT NULL,
  price_version BIGINT NOT NULL,
  old_unit_price DECIMAL(18,4) NOT NULL,
  new_unit_price DECIMAL(18,4) NOT NULL,
  affected_packages INT NOT NULL DEFAULT 0,
  operator_id BIGINT UNSIGNED NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (change_id),
  UNIQUE KEY uk_catalog_price_approval (application_no),
  UNIQUE KEY uk_catalog_product_price_version (product_id, price_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目录最终审批调价记录';

CREATE TABLE quota_package_price_change_item (
  item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  change_id BIGINT UNSIGNED NOT NULL,
  label_id BIGINT UNSIGNED NOT NULL,
  package_quantity DECIMAL(18,4) NOT NULL,
  old_unit_price DECIMAL(18,6) NOT NULL,
  new_unit_price DECIMAL(18,6) NOT NULL,
  old_amount DECIMAL(18,4) NOT NULL,
  new_amount DECIMAL(18,4) NOT NULL,
  difference_amount DECIMAL(18,4) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (item_id),
  UNIQUE KEY uk_quota_package_price_change (change_id, label_id),
  KEY idx_quota_package_price_history (label_id, item_id),
  CONSTRAINT fk_quota_price_change FOREIGN KEY (change_id) REFERENCES catalog_price_change(change_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='套包计价调整明细；原始来源成本不变';
