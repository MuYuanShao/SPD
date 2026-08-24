-- 证照管理：商品证照、供应商证照、厂家证照与合同管理，支持附件上传与阅览。
CREATE TABLE IF NOT EXISTS `license_document` (
  `license_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '证照ID',
  `license_type` VARCHAR(30) NOT NULL COMMENT '证照类型: product/supplier/manufacturer/contract',
  `license_name` VARCHAR(150) NOT NULL COMMENT '证照名称',
  `license_no` VARCHAR(150) DEFAULT NULL COMMENT '证照编号',
  `owner_type` VARCHAR(30) DEFAULT NULL COMMENT '所属对象类型',
  `owner_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '所属对象ID',
  `owner_code` VARCHAR(100) DEFAULT NULL COMMENT '所属对象编码',
  `owner_name` VARCHAR(150) DEFAULT NULL COMMENT '所属对象名称',
  `party_a` VARCHAR(150) DEFAULT NULL COMMENT '甲方（合同）',
  `party_b` VARCHAR(150) DEFAULT NULL COMMENT '乙方（合同）',
  `contract_amount` DECIMAL(18,2) DEFAULT NULL COMMENT '合同金额',
  `issue_date` DATE DEFAULT NULL COMMENT '签发日期',
  `expire_date` DATE DEFAULT NULL COMMENT '有效期至',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1有效 0失效',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`license_id`),
  KEY `idx_license_type` (`license_type`),
  KEY `idx_license_owner` (`owner_type`, `owner_id`),
  KEY `idx_license_expire` (`expire_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='证照与合同表';

INSERT IGNORE INTO `sys_permission` (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`) VALUES
(0,'证照管理','license-management',1,'/features/license-management',19);

INSERT IGNORE INTO `sys_permission` (`parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `sort_order`)
SELECT 0, '证照管理写操作', 'license-management:write', 2, NULL, 19
  FROM DUAL
 WHERE EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'license-management' AND deleted = 0);

-- 管理员与质控角色可使用证照管理
INSERT IGNORE INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.role_id, p.perm_id FROM sys_role r JOIN sys_permission p
  ON p.perm_code IN ('license-management','license-management:write')
 WHERE r.role_code IN ('admin','qc') AND r.deleted = 0 AND p.deleted = 0;
