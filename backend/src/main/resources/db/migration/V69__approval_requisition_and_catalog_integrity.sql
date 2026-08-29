-- Consolidate catalog schema under Flyway and add stable identities required by
-- approval history, source/destination requisitions, and attachment promotion.
DROP PROCEDURE IF EXISTS spd_v69_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE spd_v69_add_column_if_missing(
  IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL spd_v69_add_column_if_missing('product', 'is_volume_based', "TINYINT NOT NULL DEFAULT 0 COMMENT '是否带量'");
CALL spd_v69_add_column_if_missing('product', 'is_centralized_procurement', "TINYINT NOT NULL DEFAULT 0 COMMENT '是否集采'");
CALL spd_v69_add_column_if_missing('product', 'is_domestic', "TINYINT NOT NULL DEFAULT 1 COMMENT '是否国产'");
CALL spd_v69_add_column_if_missing('product', 'contract_code', "VARCHAR(80) NULL COMMENT '合同编码'");
CALL spd_v69_add_column_if_missing('product', 'first_category', "VARCHAR(80) NULL COMMENT '一级分类'");
CALL spd_v69_add_column_if_missing('product', 'second_category', "VARCHAR(80) NULL COMMENT '二级分类'");
CALL spd_v69_add_column_if_missing('product', 'third_category', "VARCHAR(80) NULL COMMENT '三级分类'");
CALL spd_v69_add_column_if_missing('product', 'is_chargeable', "TINYINT NOT NULL DEFAULT 1 COMMENT '是否收费'");
CALL spd_v69_add_column_if_missing('product', 'is_key_monitored', "TINYINT NOT NULL DEFAULT 0 COMMENT '是否重点监控'");
CALL spd_v69_add_column_if_missing('product', 'tender_sub_code', "VARCHAR(80) NULL COMMENT '招采子编码'");

CALL spd_v69_add_column_if_missing('pending_product_application', 'is_volume_based', "TINYINT NOT NULL DEFAULT 0 COMMENT '是否带量'");
CALL spd_v69_add_column_if_missing('pending_product_application', 'is_centralized_procurement', "TINYINT NOT NULL DEFAULT 0 COMMENT '是否集采'");
CALL spd_v69_add_column_if_missing('pending_product_application', 'is_domestic', "TINYINT NOT NULL DEFAULT 1 COMMENT '是否国产'");
CALL spd_v69_add_column_if_missing('pending_product_application', 'contract_code', "VARCHAR(80) NULL COMMENT '合同编码'");
CALL spd_v69_add_column_if_missing('pending_product_application', 'first_category', "VARCHAR(80) NULL COMMENT '一级分类'");
CALL spd_v69_add_column_if_missing('pending_product_application', 'second_category', "VARCHAR(80) NULL COMMENT '二级分类'");
CALL spd_v69_add_column_if_missing('pending_product_application', 'third_category', "VARCHAR(80) NULL COMMENT '三级分类'");
CALL spd_v69_add_column_if_missing('pending_product_application', 'is_chargeable', "TINYINT NOT NULL DEFAULT 1 COMMENT '是否收费'");
CALL spd_v69_add_column_if_missing('pending_product_application', 'is_key_monitored', "TINYINT NOT NULL DEFAULT 0 COMMENT '是否重点监控'");
CALL spd_v69_add_column_if_missing('pending_product_application', 'tender_sub_code', "VARCHAR(80) NULL COMMENT '招采子编码'");
CALL spd_v69_add_column_if_missing('pending_product_application', 'approval_round', "INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '审批轮次'");
CALL spd_v69_add_column_if_missing('department_requisition', 'source_warehouse_id', "BIGINT UNSIGNED NULL COMMENT '供应中心/一级库房ID' AFTER `warehouse_id`");
CALL spd_v69_add_column_if_missing('sys_attachment', 'source_attachment_id', "BIGINT UNSIGNED NULL COMMENT '审批归档来源附件ID' AFTER `attachment_id`");

DROP PROCEDURE spd_v69_add_column_if_missing;

CREATE TABLE IF NOT EXISTS pending_product_approval_action (
  action_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  application_id BIGINT UNSIGNED NOT NULL,
  approval_round INT UNSIGNED NOT NULL,
  flow_id BIGINT UNSIGNED NULL,
  step_id BIGINT UNSIGNED NULL,
  step_order INT NOT NULL,
  actor_id BIGINT UNSIGNED NOT NULL,
  action VARCHAR(20) NOT NULL,
  opinion VARCHAR(500) NULL,
  from_status VARCHAR(30) NOT NULL,
  to_status VARCHAR(30) NOT NULL,
  source_type VARCHAR(20) NOT NULL DEFAULT 'runtime',
  action_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (action_id),
  UNIQUE KEY uk_pending_approval_actor_step (application_id, approval_round, step_order, actor_id),
  KEY idx_pending_approval_actor_time (actor_id, action_time),
  KEY idx_pending_approval_application_round (application_id, approval_round, step_order),
  CONSTRAINT fk_pending_approval_application FOREIGN KEY (application_id)
    REFERENCES pending_product_application (application_id) ON DELETE RESTRICT,
  CONSTRAINT fk_pending_approval_actor FOREIGN KEY (actor_id)
    REFERENCES sys_user (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='待审批目录不可变审批动作';

-- Preserve the only reliable legacy decisions. Intermediate steps that cannot
-- be reconstructed are deliberately left unknown instead of inventing actors.
INSERT IGNORE INTO pending_product_approval_action (
  application_id, approval_round, step_order, actor_id, action, opinion,
  from_status, to_status, source_type, action_time
)
SELECT application_id, 1, 999, approve_by,
       CASE WHEN approval_status = 'approved' THEN 'approve'
            WHEN approval_status = 'rejected' THEN 'reject' ELSE 'return' END,
       approve_opinion, 'legacy_unknown', approval_status, 'legacy_import', COALESCE(approve_time, submit_time)
  FROM pending_product_application
 WHERE approve_by IS NOT NULL AND approval_status IN ('approved', 'rejected', 'returned');

ALTER TABLE department_requisition
  ADD KEY idx_requisition_source_warehouse (source_warehouse_id);

ALTER TABLE sys_attachment
  ADD UNIQUE KEY uk_attachment_promotion (biz_type, biz_id, source_attachment_id);
