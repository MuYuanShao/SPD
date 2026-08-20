ALTER TABLE `high_value_charge`
  ADD COLUMN `source_system` VARCHAR(40) DEFAULT NULL COMMENT '来源系统' AFTER `charge_no`,
  ADD COLUMN `external_charge_no` VARCHAR(100) DEFAULT NULL COMMENT '外部计费号' AFTER `source_system`,
  ADD COLUMN `operation_no` VARCHAR(100) DEFAULT NULL COMMENT '手术/治疗单号' AFTER `external_charge_no`,
  ADD COLUMN `udi_code` VARCHAR(120) DEFAULT NULL COMMENT 'UDI' AFTER `operation_no`,
  ADD COLUMN `unique_code` VARCHAR(120) DEFAULT NULL COMMENT '唯一码' AFTER `udi_code`,
  ADD COLUMN `charge_time` DATETIME DEFAULT NULL COMMENT '计费时间' AFTER `status`,
  ADD UNIQUE KEY `uk_high_value_external_charge` (`external_charge_no`),
  ADD KEY `idx_high_value_unique_code` (`unique_code`);

UPDATE `udi_trace_event`
   SET `event_type` = 'high_value_billing',
       `event_name` = '手麻计费回传',
       `event_time` = '2026-07-07 11:35:00',
       `remark` = '手麻/手术系统回传患者与耗材计费数据，SPD 据此扣减库存并记录追溯'
 WHERE `event_no` = 'UT202607070004'
   AND `event_type` = 'department_consumption';

UPDATE `udi_trace_event`
   SET `sort_order` = 40
 WHERE `event_no` = 'UT202607070004';

DELETE FROM `udi_trace_event`
 WHERE `event_no` = 'UT202607070005';

UPDATE `udi_trace_code`
   SET `last_event_name` = '手麻计费回传',
       `last_event_time` = '2026-07-07 11:35:00'
 WHERE `unique_code` = 'HV202607070001';
