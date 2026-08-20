CREATE TABLE IF NOT EXISTS `campus` (
  `campus_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '院区ID',
  `campus_code` VARCHAR(50) NOT NULL COMMENT '院区编码',
  `campus_name` VARCHAR(80) NOT NULL COMMENT '院区名称',
  `address` VARCHAR(200) DEFAULT NULL COMMENT '院区地址',
  `manager_name` VARCHAR(50) DEFAULT NULL COMMENT '负责人',
  `phone` VARCHAR(30) DEFAULT NULL COMMENT '联系电话',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`campus_id`),
  UNIQUE KEY `uk_campus_code` (`campus_code`),
  UNIQUE KEY `uk_campus_name` (`campus_name`, `deleted`),
  KEY `idx_campus_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='院区主数据';

INSERT IGNORE INTO `campus` (`campus_code`, `campus_name`, `sort_order`, `status`)
SELECT CONCAT('CAMPUS-', LPAD(ROW_NUMBER() OVER (ORDER BY campus_name), 3, '0')),
       campus_name,
       ROW_NUMBER() OVER (ORDER BY campus_name),
       1
  FROM (
        SELECT DISTINCT campus_name
          FROM sys_dept
         WHERE deleted = 0 AND campus_name IS NOT NULL AND campus_name <> ''
        UNION
        SELECT DISTINCT campus_name
          FROM warehouse
         WHERE deleted = 0 AND campus_name IS NOT NULL AND campus_name <> ''
       ) existing_campus;

INSERT IGNORE INTO `campus` (`campus_code`, `campus_name`, `sort_order`, `status`)
VALUES ('CAMPUS-MAIN', '主院区', 1, 1);
