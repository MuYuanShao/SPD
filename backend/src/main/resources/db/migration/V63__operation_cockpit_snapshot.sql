CREATE TABLE IF NOT EXISTS `operation_cockpit_snapshot` (
  `snapshot_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '快照ID',
  `statistics_month` DATE NOT NULL COMMENT '统计月份（月份首日）',
  `statistics_date` DATE NOT NULL COMMENT '统计任务执行日期',
  `current_amount` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '本月消耗金额',
  `previous_amount` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '上月消耗金额',
  `month_on_month` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '金额环比百分比',
  `department_count` INT NOT NULL DEFAULT 0 COMMENT '本月消耗科室数',
  `warning_count` INT NOT NULL DEFAULT 0 COMMENT '重点监控预警数',
  `categories_json` JSON NOT NULL COMMENT '耗材分类对比',
  `trend_json` JSON NOT NULL COMMENT '近十二个月趋势',
  `departments_json` JSON NOT NULL COMMENT '科室消耗排名',
  `focused_products_json` JSON NOT NULL COMMENT '重点监控耗材排名',
  `alerts_json` JSON NOT NULL COMMENT '库存与异常提醒',
  `generated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照生成时间',
  PRIMARY KEY (`snapshot_id`),
  UNIQUE KEY `uk_operation_cockpit_month` (`statistics_month`),
  KEY `idx_operation_cockpit_generated_at` (`generated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运营驾驶舱月度展示快照';
