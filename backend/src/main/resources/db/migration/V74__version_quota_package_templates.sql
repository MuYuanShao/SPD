-- 定数包模板不可变版本、申领快照及细粒度操作权限。
ALTER TABLE `quota_package_template`
  ADD COLUMN `version_no` INT NOT NULL DEFAULT 1 COMMENT '模板版本' AFTER `template_name`,
  ADD COLUMN `is_current` TINYINT NOT NULL DEFAULT 1 COMMENT '是否当前版本' AFTER `version_no`,
  ADD COLUMN `superseded_by_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '后继模板版本ID' AFTER `is_current`,
  ADD COLUMN `effective_from` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间' AFTER `superseded_by_id`,
  ADD COLUMN `effective_to` DATETIME DEFAULT NULL COMMENT '失效时间' AFTER `effective_from`,
  DROP INDEX `uk_template_code`,
  ADD UNIQUE KEY `uk_template_code_version` (`template_code`, `version_no`),
  ADD KEY `idx_template_current` (`template_code`, `is_current`, `deleted`),
  ADD KEY `idx_template_superseded` (`superseded_by_id`);

ALTER TABLE `department_requisition_item`
  ADD COLUMN `quota_template_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '定数包模板版本ID' AFTER `item_type`,
  ADD COLUMN `quota_template_version` INT DEFAULT NULL COMMENT '定数包模板版本快照' AFTER `quota_template_id`,
  ADD COLUMN `quota_package_quantity` DECIMAL(18,4) DEFAULT NULL COMMENT '包内基础数量快照' AFTER `quota_template_version`,
  ADD COLUMN `quota_package_unit` VARCHAR(20) DEFAULT NULL COMMENT '包装单位快照' AFTER `quota_package_quantity`,
  ADD KEY `idx_requisition_quota_template` (`quota_template_id`);

UPDATE `department_requisition_item` dri
JOIN `department_requisition` dr ON dr.requisition_id = dri.requisition_id
JOIN `quota_safety_stock` qss ON qss.dept_id = dr.dept_id AND qss.product_id = dri.product_id
JOIN `quota_package_template` qpt ON qpt.template_id = qss.template_id
JOIN `quota_package_template_item` qpti ON qpti.template_id = qpt.template_id AND qpti.deleted = 0
   SET dri.quota_template_id = qpt.template_id,
       dri.quota_template_version = qpt.version_no,
       dri.quota_package_quantity = qpti.quantity,
       dri.quota_package_unit = qpti.unit
 WHERE dri.item_type = 'quota_package'
   AND dri.quota_template_id IS NULL;

INSERT IGNORE INTO `sys_permission`
  (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`, `status`, `deleted`)
VALUES
  (0, '定数包任务创建', 'quota-packing-task:create', 2, NULL, 221, 1, 0),
  (0, '定数包任务确认', 'quota-packing-task:confirm', 2, NULL, 222, 1, 0),
  (0, '定数包任务取消', 'quota-packing-task:cancel', 2, NULL, 223, 1, 0),
  (0, '定数包任务重算', 'quota-packing-task:recalculate', 2, NULL, 224, 1, 0),
  (0, '定数包任务终止', 'quota-packing-task:terminate', 2, NULL, 225, 1, 0),
  (0, '定数包标签打印', 'quota-package-label:print', 2, NULL, 231, 1, 0),
  (0, '定数包扫码签收', 'quota-package-label:sign', 2, NULL, 232, 1, 0),
  (0, '定数包扫码消耗', 'quota-package-label:consume', 2, NULL, 233, 1, 0);

INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id
  FROM `sys_role` r CROSS JOIN `sys_permission` p
 WHERE r.role_code = 'admin' AND r.deleted = 0
   AND p.perm_code LIKE 'quota-%' AND p.deleted = 0;
