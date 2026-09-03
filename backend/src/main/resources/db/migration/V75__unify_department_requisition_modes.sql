-- 科室申领统一模式、智能分析追溯及高值拣配延迟绑定。
ALTER TABLE `department_requisition_item`
  ADD COLUMN `requested_package_count` DECIMAL(18,4) DEFAULT NULL COMMENT '申请包数快照' AFTER `quota_package_unit`,
  ADD COLUMN `picked_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计拣配基础数量' AFTER `requested_package_count`;

ALTER TABLE `replenishment_smart_analysis`
  ADD COLUMN `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '稳定科室ID' AFTER `analysis_id`,
  ADD COLUMN `warehouse_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '目标库房ID' AFTER `dept_id`,
  ADD COLUMN `source_warehouse_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源中心库ID' AFTER `warehouse_id`,
  ADD COLUMN `created_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '分析人' AFTER `source_warehouse_id`,
  ADD COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT 'draft/generated' AFTER `created_by`,
  ADD KEY `idx_smart_analysis_scope` (`dept_id`, `warehouse_id`, `created_by`, `status`);

ALTER TABLE `replenishment_smart_analysis_item`
  ADD COLUMN `product_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '稳定商品ID' AFTER `analysis_id`,
  ADD COLUMN `dept_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '稳定科室ID' AFTER `product_id`,
  ADD COLUMN `warehouse_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '目标库房ID' AFTER `dept_id`,
  ADD COLUMN `source_warehouse_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源中心库ID' AFTER `warehouse_id`,
  ADD COLUMN `item_mode` VARCHAR(30) NOT NULL DEFAULT 'loose' COMMENT '申领模式' AFTER `product_id`,
  ADD COLUMN `quota_template_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '定数包模板版本ID' AFTER `item_mode`,
  ADD COLUMN `quota_template_version` INT DEFAULT NULL COMMENT '定数包模板版本' AFTER `quota_template_id`,
  ADD COLUMN `package_quantity` DECIMAL(18,4) DEFAULT NULL COMMENT '包内基础数量' AFTER `quota_template_version`,
  ADD COLUMN `source_available_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '分析时来源库可用量' AFTER `current_qty`,
  ADD COLUMN `manual_adjusted_qty` DECIMAL(18,4) DEFAULT NULL COMMENT '人工调整申领量' AFTER `recommended_qty`,
  ADD COLUMN `selected` TINYINT NOT NULL DEFAULT 1 COMMENT '是否选中生成' AFTER `manual_adjusted_qty`;

CREATE TABLE `replenishment_analysis_requisition` (
  `analysis_id` BIGINT UNSIGNED NOT NULL,
  `requisition_id` BIGINT UNSIGNED NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`analysis_id`, `requisition_id`),
  UNIQUE KEY `uk_analysis_requisition` (`requisition_id`),
  CONSTRAINT `fk_analysis_requisition_analysis` FOREIGN KEY (`analysis_id`) REFERENCES `replenishment_smart_analysis` (`analysis_id`),
  CONSTRAINT `fk_analysis_requisition_document` FOREIGN KEY (`requisition_id`) REFERENCES `department_requisition` (`requisition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能补货分析生成申领关联';

INSERT IGNORE INTO `sys_permission`
  (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`, `status`, `deleted`)
VALUES
  (0, '科室申领查看', 'department-requisition:read', 2, NULL, 281, 1, 0),
  (0, '科室申领创建', 'department-requisition:create', 2, NULL, 282, 1, 0),
  (0, '科室智能补货', 'department-requisition:smart-analysis', 2, NULL, 283, 1, 0),
  (0, '科室申领审批', 'department-requisition:approve', 2, NULL, 284, 1, 0),
  (0, '科室申领拣配', 'department-requisition:pick', 2, NULL, 285, 1, 0);

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT DISTINCT rp.role_id, fine.perm_id
  FROM `sys_role_perm` rp
  JOIN `sys_permission` legacy ON legacy.perm_id = rp.perm_id
  JOIN `sys_permission` fine ON (
       (legacy.perm_code = 'department-requisition' AND fine.perm_code = 'department-requisition:read') OR
       (legacy.perm_code = 'department-requisition:write' AND fine.perm_code IN (
         'department-requisition:create', 'department-requisition:smart-analysis', 'department-requisition:approve')) OR
       (legacy.perm_code = 'picking-delivery:write' AND fine.perm_code = 'department-requisition:pick'))
 WHERE legacy.perm_code IN ('department-requisition', 'department-requisition:write', 'picking-delivery:write')
   AND fine.deleted = 0;
