-- 补齐表和字段中文说明，保留原列定义、默认值、生成表达式及索引约束。
-- 基于V86结构；不修改业务数据。

ALTER TABLE `approval_flow`
  MODIFY COLUMN `revision_no` int NOT NULL DEFAULT '1' COMMENT '审批流程当前配置版本；修改步骤后递增',
  MODIFY COLUMN `active_catalog_scope` varchar(191) GENERATED ALWAYS AS ((case when ((`feature_code` = _utf8mb4'pending-product-catalog') and (`node_code` = _utf8mb4'initial-review') and (`status` = 1) and (`deleted` = 0)) then concat(`scope_type`,_utf8mb4':',(case when (`scope_type` = _utf8mb4'global') then _utf8mb4'default' when (`scope_type` = _utf8mb4'department') then coalesce(cast(`dept_id` as char charset utf8mb4),`scope_id`) when (`scope_type` = _utf8mb4'role') then lower(replace(`scope_id`,_utf8mb4'ROLE_',_utf8mb4'')) else `scope_id` end)) else NULL end)) STORED COMMENT '启用目录顺序审批的归一化适用范围，用于防止同范围流程冲突',
  MODIFY COLUMN `non_catalog_scope_guard` tinyint GENERATED ALWAYS AS ((case when ((`feature_code` = _utf8mb4'pending-product-catalog') and (`node_code` = _utf8mb4'initial-review')) then NULL else 1 end)) STORED COMMENT '非目录流程唯一约束辅助列：目录顺序审批为NULL，其他流程为1';

ALTER TABLE `approval_flow_step`
  MODIFY COLUMN `revision_no` int NOT NULL DEFAULT '1' COMMENT '审批步骤所属配置版本；旧版本归档以保留历史引用';

ALTER TABLE `batch_price_adjustment`
  MODIFY COLUMN `adjustment_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '批次调价申请主键',
  MODIFY COLUMN `adjustment_no` varchar(50) NOT NULL COMMENT '批次调价申请单号',
  MODIFY COLUMN `batch_id` bigint unsigned NOT NULL COMMENT '库存批次ID，关联inventory_batch',
  MODIFY COLUMN `old_unit_price` decimal(18,4) NOT NULL COMMENT '调整前的采购单价',
  MODIFY COLUMN `new_unit_price` decimal(18,4) NOT NULL COMMENT '调整后的采购单价',
  MODIFY COLUMN `affected_qty` decimal(18,4) NOT NULL COMMENT '调价影响数量（商品最小单位）',
  MODIFY COLUMN `reason` varchar(500) DEFAULT NULL COMMENT '业务原因',
  MODIFY COLUMN `status` varchar(30) NOT NULL DEFAULT 'draft' COMMENT '批次调价审批状态',
  MODIFY COLUMN `approve_by` bigint unsigned DEFAULT NULL COMMENT '审批人用户ID，关联sys_user',
  MODIFY COLUMN `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = '库存批次价格调整申请';

ALTER TABLE `cold_chain_exception`
  MODIFY COLUMN `event_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '冷链异常登记主键',
  MODIFY COLUMN `event_no` varchar(50) NOT NULL COMMENT '冷链异常登记单号',
  MODIFY COLUMN `warehouse_name` varchar(80) NOT NULL COMMENT '库房名称',
  MODIFY COLUMN `product_code` varchar(50) NOT NULL COMMENT '商品编码',
  MODIFY COLUMN `product_name` varchar(120) NOT NULL COMMENT '商品名称',
  MODIFY COLUMN `temperature` decimal(10,2) NOT NULL COMMENT '异常登记温度（摄氏度）',
  MODIFY COLUMN `severity` varchar(30) NOT NULL COMMENT '冷链异常严重程度',
  MODIFY COLUMN `status` varchar(30) NOT NULL COMMENT '冷链异常处置状态，pending_dispose表示待处置',
  MODIFY COLUMN `event_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '业务事件发生时间',
  COMMENT = '冷链温度异常登记与处置';

ALTER TABLE `consumption_red_flush`
  MODIFY COLUMN `flush_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '科室消耗红冲记录主键',
  MODIFY COLUMN `flush_no` varchar(50) NOT NULL COMMENT '科室消耗红冲单号',
  MODIFY COLUMN `source_consumption_no` varchar(50) NOT NULL COMMENT '被红冲的原科室消耗单号',
  MODIFY COLUMN `flush_type` varchar(50) NOT NULL COMMENT '红冲业务类型',
  MODIFY COLUMN `status` varchar(30) NOT NULL COMMENT '科室消耗红冲审批状态',
  MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = '科室消耗红冲记录';

ALTER TABLE `data_quality_issue`
  MODIFY COLUMN `resolution_status` varchar(30) NOT NULL DEFAULT 'resolved' COMMENT '数据质量问题处理状态：resolved已修复、reimport_required需重新导入';

ALTER TABLE `department_requisition_trace_code`
  MODIFY COLUMN `requisition_id` bigint unsigned NOT NULL COMMENT '科室申领单ID，关联department_requisition',
  MODIFY COLUMN `requisition_item_id` bigint unsigned NOT NULL COMMENT '科室申领明细ID，关联department_requisition_item',
  MODIFY COLUMN `trace_code_id` bigint unsigned NOT NULL COMMENT '稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = '科室申领单与收费耗材追溯单元关联';

ALTER TABLE `flyway_schema_history`
  MODIFY COLUMN `installed_rank` int NOT NULL COMMENT '迁移安装顺序主键',
  MODIFY COLUMN `version` varchar(50) DEFAULT NULL COMMENT '并发控制版本号',
  MODIFY COLUMN `description` varchar(200) NOT NULL COMMENT '迁移版本说明',
  MODIFY COLUMN `type` varchar(20) NOT NULL COMMENT '迁移类型',
  MODIFY COLUMN `script` varchar(1000) NOT NULL COMMENT '迁移脚本文件名',
  MODIFY COLUMN `checksum` int DEFAULT NULL COMMENT '迁移脚本校验和',
  MODIFY COLUMN `installed_by` varchar(100) NOT NULL COMMENT '执行迁移的数据库用户',
  MODIFY COLUMN `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '迁移安装时间',
  MODIFY COLUMN `execution_time` int NOT NULL COMMENT '迁移执行耗时（毫秒）',
  MODIFY COLUMN `success` tinyint(1) NOT NULL COMMENT '迁移是否成功：0失败、1成功',
  COMMENT = 'Flyway数据库迁移版本与执行历史（框架维护）';

ALTER TABLE `high_value_charge`
  MODIFY COLUMN `charge_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'SPD收费耗材记录主键',
  MODIFY COLUMN `charge_no` varchar(50) NOT NULL COMMENT 'SPD收费耗材记录业务编号',
  MODIFY COLUMN `udi_code` varchar(120) DEFAULT NULL COMMENT 'UDI标识文本',
  MODIFY COLUMN `trace_code_id` bigint unsigned DEFAULT NULL COMMENT '稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成',
  MODIFY COLUMN `dept_name` varchar(80) NOT NULL COMMENT '科室名称',
  MODIFY COLUMN `patient_no` varchar(50) NOT NULL COMMENT '患者编号',
  MODIFY COLUMN `product_code` varchar(50) NOT NULL COMMENT '商品编码',
  MODIFY COLUMN `product_name` varchar(120) NOT NULL COMMENT '商品名称',
  MODIFY COLUMN `quantity` decimal(18,4) NOT NULL COMMENT '业务数量',
  MODIFY COLUMN `amount` decimal(18,4) NOT NULL COMMENT '金额',
  MODIFY COLUMN `status` varchar(30) NOT NULL COMMENT '收费耗材计费处理状态',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = 'SPD收费耗材使用记录（接收外部计费后记录使用及扣减库存）';

ALTER TABLE `inventory_balance`
  MODIFY COLUMN `location_key` bigint unsigned GENERATED ALWAYS AS (coalesce(`location_id`,0)) STORED COMMENT '归一化货位唯一键：无货位时为0，用于库存余额唯一约束';

ALTER TABLE `inventory_batch_trace_code`
  MODIFY COLUMN `batch_id` bigint unsigned NOT NULL COMMENT '库存批次ID，关联inventory_batch',
  MODIFY COLUMN `trace_code_id` bigint unsigned NOT NULL COMMENT '稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成',
  MODIFY COLUMN `receiving_item_id` bigint unsigned NOT NULL COMMENT '收货验收明细ID，关联receiving_order_item',
  MODIFY COLUMN `current_warehouse_id` bigint unsigned DEFAULT NULL COMMENT '追溯单元当前库房ID，关联warehouse',
  MODIFY COLUMN `lifecycle_status` varchar(40) NOT NULL DEFAULT 'in_stock' COMMENT '追溯单元生命周期状态',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = '收货库存批次与收费耗材追溯单元关联';

ALTER TABLE `inventory_event`
  MODIFY COLUMN `transaction_type_code` varchar(40) DEFAULT NULL COMMENT '稳定的库存账务交易类型编码',
  MODIFY COLUMN `event_category` varchar(20) NOT NULL DEFAULT 'quantity' COMMENT '事件类别：quantity数量变动、valuation价值变动',
  MODIFY COLUMN `dept_id_snapshot` bigint unsigned DEFAULT NULL COMMENT '事件发生时科室ID快照',
  MODIFY COLUMN `dept_name_snapshot` varchar(100) DEFAULT NULL COMMENT '事件发生时科室名称快照',
  MODIFY COLUMN `warehouse_code_snapshot` varchar(50) DEFAULT NULL COMMENT '事件发生时库房编码快照',
  MODIFY COLUMN `warehouse_name_snapshot` varchar(100) DEFAULT NULL COMMENT '事件发生时库房名称快照',
  MODIFY COLUMN `product_code_snapshot` varchar(64) DEFAULT NULL COMMENT '事件发生时商品编码快照',
  MODIFY COLUMN `product_name_snapshot` varchar(255) DEFAULT NULL COMMENT '事件发生时商品名称快照',
  MODIFY COLUMN `spec_model_snapshot` varchar(255) DEFAULT NULL COMMENT '事件发生时规格型号快照',
  MODIFY COLUMN `registration_no_snapshot` varchar(120) DEFAULT NULL COMMENT '事件发生时注册证号快照',
  MODIFY COLUMN `unit_snapshot` varchar(30) DEFAULT NULL COMMENT '事件发生时商品计量单位快照',
  MODIFY COLUMN `manufacturer_name_snapshot` varchar(255) DEFAULT NULL COMMENT '事件发生时厂家名称快照',
  MODIFY COLUMN `supplier_id_snapshot` bigint unsigned DEFAULT NULL COMMENT '事件发生时供应商ID，关联supplier快照',
  MODIFY COLUMN `supplier_name_snapshot` varchar(255) DEFAULT NULL COMMENT '事件发生时供应商名称快照',
  MODIFY COLUMN `system_batch_no_snapshot` varchar(50) DEFAULT NULL COMMENT '事件发生时系统库存批次号快照',
  MODIFY COLUMN `production_batch_no_snapshot` varchar(50) DEFAULT NULL COMMENT '事件发生时生产批号快照',
  MODIFY COLUMN `unit_price_snapshot` decimal(18,4) DEFAULT NULL COMMENT '事件发生时采购单价快照',
  MODIFY COLUMN `amount_snapshot` decimal(18,4) DEFAULT NULL COMMENT '事件发生时带正负号的金额快照',
  MODIFY COLUMN `old_unit_price` decimal(18,4) DEFAULT NULL COMMENT '调整前的采购单价',
  MODIFY COLUMN `new_unit_price` decimal(18,4) DEFAULT NULL COMMENT '调整后的采购单价',
  MODIFY COLUMN `affected_qty_snapshot` decimal(18,4) DEFAULT NULL COMMENT '价值变动事件影响的商品数量快照',
  MODIFY COLUMN `value_change` decimal(18,4) DEFAULT NULL COMMENT '调价价值变化金额，正数增加、负数减少',
  MODIFY COLUMN `snapshot_origin` varchar(30) NOT NULL DEFAULT 'legacy_backfill' COMMENT '事件快照来源：captured发生时采集、legacy_backfill历史回填';

ALTER TABLE `inventory_event_trace_code`
  MODIFY COLUMN `event_id` bigint unsigned NOT NULL COMMENT '不可变库存事件ID，关联inventory_event',
  MODIFY COLUMN `trace_code_id` bigint unsigned NOT NULL COMMENT '稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成',
  MODIFY COLUMN `trace_type` varchar(40) NOT NULL COMMENT '追溯关联类型：high_value_unit收费耗材单元、quota_package定数包',
  MODIFY COLUMN `linked_quantity` decimal(18,4) NOT NULL DEFAULT '1.0000' COMMENT '该追溯身份代表的商品数量',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = '不可变库存事件与收费耗材或定数包追溯身份关联';

-- 历史库存查询快照表：仅在已有表时补注释，不创建或恢复业务模块。
SET @comment_ddl = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_quota_package_stock'), 'ALTER TABLE `inventory_quota_package_stock`
  MODIFY COLUMN `label_id` bigint unsigned NOT NULL COMMENT ''定数包标签ID，关联quota_package_label'',
  MODIFY COLUMN `package_code` varchar(100) NOT NULL COMMENT ''定数包标签编号'',
  MODIFY COLUMN `status` varchar(30) NOT NULL COMMENT ''来源定数包标签业务状态'',
  MODIFY COLUMN `inventory_state` varchar(30) NOT NULL COMMENT ''库存展示状态'',
  MODIFY COLUMN `template_code` varchar(160) NOT NULL COMMENT ''定数包模板编码'',
  MODIFY COLUMN `template_name` varchar(200) NOT NULL COMMENT ''定数包模板名称'',
  MODIFY COLUMN `warehouse_name` varchar(100) NOT NULL COMMENT ''库房名称'',
  MODIFY COLUMN `product_code` varchar(80) NOT NULL COMMENT ''商品编码'',
  MODIFY COLUMN `product_name` varchar(200) NOT NULL COMMENT ''商品名称'',
  MODIFY COLUMN `spec_model` varchar(200) DEFAULT NULL COMMENT ''规格型号'',
  MODIFY COLUMN `unit` varchar(30) DEFAULT NULL COMMENT ''商品计量单位'',
  MODIFY COLUMN `package_quantity` decimal(18,4) NOT NULL COMMENT ''该定数包所含商品数量（商品最小单位）'',
  MODIFY COLUMN `source_batches` text COMMENT ''定数包来源批次展示文本'',
  MODIFY COLUMN `source_update_time` datetime DEFAULT NULL COMMENT ''源业务记录最近更新时间'',
  MODIFY COLUMN `refresh_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''库存查询快照刷新时间''', 'SELECT 1');
PREPARE comment_stmt FROM @comment_ddl;
EXECUTE comment_stmt;
DEALLOCATE PREPARE comment_stmt;

ALTER TABLE `inventory_stocktaking`
  MODIFY COLUMN `stocktaking_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '盘点单ID，关联inventory_stocktaking',
  MODIFY COLUMN `stocktaking_no` varchar(50) NOT NULL COMMENT '盘点单号',
  MODIFY COLUMN `warehouse_id` bigint unsigned NOT NULL COMMENT '库房ID，关联warehouse',
  MODIFY COLUMN `stocktaking_type` varchar(30) NOT NULL COMMENT '盘点业务类型',
  MODIFY COLUMN `status` varchar(30) NOT NULL DEFAULT 'draft' COMMENT '盘点单审批与执行状态',
  MODIFY COLUMN `reason` varchar(500) DEFAULT NULL COMMENT '业务原因',
  MODIFY COLUMN `approve_by` bigint unsigned DEFAULT NULL COMMENT '审批人用户ID，关联sys_user',
  MODIFY COLUMN `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = '库存盘点单';

ALTER TABLE `inventory_stocktaking_item`
  MODIFY COLUMN `item_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '业务明细主键',
  MODIFY COLUMN `stocktaking_id` bigint unsigned NOT NULL COMMENT '盘点单ID，关联inventory_stocktaking',
  MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID，关联product',
  MODIFY COLUMN `system_qty` decimal(18,4) NOT NULL COMMENT '盘点时账面库存数量（商品最小单位）',
  MODIFY COLUMN `diff_reason` varchar(500) DEFAULT NULL COMMENT '盘点差异原因',
  COMMENT = '库存盘点单商品明细';

-- 历史库存查询快照表：仅在已有表时补注释，不创建或恢复业务模块。
SET @comment_ddl = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_trace_code_stock'), 'ALTER TABLE `inventory_trace_code_stock`
  MODIFY COLUMN `trace_code_id` bigint unsigned NOT NULL COMMENT ''稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成'',
  MODIFY COLUMN `unique_code` varchar(120) NOT NULL COMMENT ''业务唯一码文本'',
  MODIFY COLUMN `udi_code` varchar(255) DEFAULT NULL COMMENT ''UDI标识文本'',
  MODIFY COLUMN `warehouse_name` varchar(100) DEFAULT NULL COMMENT ''库房名称'',
  MODIFY COLUMN `product_code` varchar(80) NOT NULL COMMENT ''商品编码'',
  MODIFY COLUMN `product_name` varchar(200) NOT NULL COMMENT ''商品名称'',
  MODIFY COLUMN `spec_model` varchar(200) DEFAULT NULL COMMENT ''规格型号'',
  MODIFY COLUMN `system_batch_no` varchar(100) DEFAULT NULL COMMENT ''系统库存批次号'',
  MODIFY COLUMN `production_batch_no` varchar(100) DEFAULT NULL COMMENT ''生产批号'',
  MODIFY COLUMN `expire_date` date DEFAULT NULL COMMENT ''有效期截止日期'',
  MODIFY COLUMN `status` varchar(30) NOT NULL COMMENT ''来源追溯码业务状态'',
  MODIFY COLUMN `inventory_state` varchar(30) NOT NULL COMMENT ''库存展示状态'',
  MODIFY COLUMN `source_update_time` datetime DEFAULT NULL COMMENT ''源业务记录最近更新时间'',
  MODIFY COLUMN `refresh_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''库存查询快照刷新时间''', 'SELECT 1');
PREPARE comment_stmt FROM @comment_ddl;
EXECUTE comment_stmt;
DEALLOCATE PREPARE comment_stmt;

-- 历史库存查询快照表：仅在已有表时补注释，不创建或恢复业务模块。
SET @comment_ddl = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_warehouse_product_stock'), 'ALTER TABLE `inventory_warehouse_product_stock`
  MODIFY COLUMN `warehouse_id` bigint unsigned NOT NULL COMMENT ''库房ID，关联warehouse'',
  MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT ''商品ID，关联product'',
  MODIFY COLUMN `unit_price` decimal(18,4) NOT NULL COMMENT ''采购单价'',
  MODIFY COLUMN `warehouse_name` varchar(100) NOT NULL COMMENT ''库房名称'',
  MODIFY COLUMN `product_code` varchar(80) NOT NULL COMMENT ''商品编码'',
  MODIFY COLUMN `product_name` varchar(200) NOT NULL COMMENT ''商品名称'',
  MODIFY COLUMN `spec_model` varchar(200) DEFAULT NULL COMMENT ''规格型号'',
  MODIFY COLUMN `unit` varchar(30) DEFAULT NULL COMMENT ''商品计量单位'',
  MODIFY COLUMN `batch_count` int NOT NULL DEFAULT ''0'' COMMENT ''参与聚合的库存批次数'',
  MODIFY COLUMN `available_qty` decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''可用库存数量（商品最小单位）'',
  MODIFY COLUMN `locked_qty` decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''锁定库存数量（商品最小单位）'',
  MODIFY COLUMN `in_transit_qty` decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''在途库存数量（商品最小单位）'',
  MODIFY COLUMN `isolated_qty` decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''隔离库存数量（商品最小单位）'',
  MODIFY COLUMN `package_loose_qty` decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''定数包内商品数量（折算商品最小单位）'',
  MODIFY COLUMN `total_qty` decimal(18,4) NOT NULL DEFAULT ''0.0000'' COMMENT ''库存合计数量（商品最小单位）'',
  MODIFY COLUMN `refresh_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''库存查询快照刷新时间''', 'SELECT 1');
PREPARE comment_stmt FROM @comment_ddl;
EXECUTE comment_stmt;
DEALLOCATE PREPARE comment_stmt;

ALTER TABLE `license_revision`
  MODIFY COLUMN `revision_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '证照历史版本主键',
  MODIFY COLUMN `license_id` bigint unsigned NOT NULL COMMENT '证照ID，关联license_document',
  MODIFY COLUMN `revision_no` int NOT NULL COMMENT '该证照历史版本号，与证照ID共同唯一',
  MODIFY COLUMN `operation_type` varchar(30) NOT NULL COMMENT '证照历史操作类型：create新增、update修改、renew续证',
  MODIFY COLUMN `snapshot_json` json NOT NULL COMMENT '证照该版本的完整业务快照（JSON）',
  MODIFY COLUMN `operator_id` bigint unsigned DEFAULT NULL COMMENT '操作人用户ID，关联sys_user',
  MODIFY COLUMN `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名或账号',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间';

ALTER TABLE `mobile_operation_receipt`
  MODIFY COLUMN `user_id` bigint unsigned NOT NULL COMMENT '用户ID，关联sys_user',
  MODIFY COLUMN `operation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端操作UUID，与用户ID共同构成幂等键',
  MODIFY COLUMN `device_id` varchar(128) NOT NULL COMMENT '移动端设备标识',
  MODIFY COLUMN `kind` varchar(32) NOT NULL COMMENT '移动端操作类型编码',
  MODIFY COLUMN `task_id` bigint unsigned NOT NULL COMMENT '操作关联业务任务ID，按kind区分对应业务',
  MODIFY COLUMN `dept_id` bigint unsigned NOT NULL COMMENT '科室ID',
  MODIFY COLUMN `warehouse_id` bigint unsigned NOT NULL COMMENT '库房ID，关联warehouse',
  MODIFY COLUMN `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作载荷SHA-256摘要，用于检查同一幂等键的请求一致性',
  MODIFY COLUMN `status` varchar(16) NOT NULL COMMENT '幂等回执状态：pending处理中、succeeded已成功',
  MODIFY COLUMN `result_json` json DEFAULT NULL COMMENT '移动端操作处理结果（JSON），用于幂等重试返回',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '移动端操作回执创建时间',
  MODIFY COLUMN `completed_at` datetime(6) DEFAULT NULL COMMENT '移动端操作完成时间',
  COMMENT = '移动端业务操作幂等回执';

ALTER TABLE `pda_offline_record`
  MODIFY COLUMN `record_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'PDA离线上传记录主键',
  MODIFY COLUMN `record_no` varchar(50) NOT NULL COMMENT 'PDA离线上传记录编号',
  MODIFY COLUMN `device_no` varchar(50) NOT NULL COMMENT 'PDA设备编号',
  MODIFY COLUMN `operation_type` varchar(50) NOT NULL COMMENT '业务操作类型',
  MODIFY COLUMN `payload` json NOT NULL COMMENT 'PDA离线操作原始业务数据（JSON）',
  MODIFY COLUMN `status` varchar(30) NOT NULL COMMENT '离线数据回放处理状态，replayed表示已回放',
  MODIFY COLUMN `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'PDA离线数据上传时间',
  COMMENT = 'PDA离线业务上传记录';

ALTER TABLE `pending_product_approval_action`
  MODIFY COLUMN `action_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '目录审批动作主键',
  MODIFY COLUMN `application_id` bigint unsigned NOT NULL COMMENT '目录申请ID，关联pending_product_application',
  MODIFY COLUMN `approval_round` int unsigned NOT NULL COMMENT '审批轮次，退回重提递增并保留旧轮次',
  MODIFY COLUMN `flow_id` bigint unsigned DEFAULT NULL COMMENT '审批流程ID，关联approval_flow',
  MODIFY COLUMN `step_id` bigint unsigned DEFAULT NULL COMMENT '来源审批步骤ID，关联approval_flow_step',
  MODIFY COLUMN `step_order` int NOT NULL COMMENT '来源审批步骤顺序',
  MODIFY COLUMN `actor_id` bigint unsigned NOT NULL COMMENT '审批操作人用户ID，关联sys_user',
  MODIFY COLUMN `action` varchar(20) NOT NULL COMMENT '审批操作类型：approve通过、return退回、reject拒绝',
  MODIFY COLUMN `opinion` varchar(500) DEFAULT NULL COMMENT '审批意见',
  MODIFY COLUMN `from_status` varchar(30) NOT NULL COMMENT '审批操作前的申请状态',
  MODIFY COLUMN `to_status` varchar(30) NOT NULL COMMENT '审批操作后的申请状态',
  MODIFY COLUMN `source_type` varchar(20) NOT NULL DEFAULT 'runtime' COMMENT '审批动作来源类型，legacy_import表示历史数据补录',
  MODIFY COLUMN `action_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审批操作发生时间';

ALTER TABLE `pending_product_approval_route_step`
  MODIFY COLUMN `route_step_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '目录申请审批路由快照主键',
  MODIFY COLUMN `application_id` bigint unsigned NOT NULL COMMENT '目录申请ID，关联pending_product_application',
  MODIFY COLUMN `approval_round` int unsigned NOT NULL COMMENT '审批轮次，退回重提递增并保留旧轮次',
  MODIFY COLUMN `route_order` int unsigned NOT NULL COMMENT '本轮审批连续顺序，从1开始',
  MODIFY COLUMN `feature_code` varchar(80) NOT NULL COMMENT '业务模块编码',
  MODIFY COLUMN `node_code` varchar(80) NOT NULL COMMENT '审批业务节点编码',
  MODIFY COLUMN `flow_id` bigint unsigned NOT NULL COMMENT '审批流程ID，关联approval_flow',
  MODIFY COLUMN `source_step_id` bigint unsigned NOT NULL COMMENT '提交时来源审批步骤ID，关联approval_flow_step',
  MODIFY COLUMN `source_step_order` int unsigned NOT NULL COMMENT '提交时来源配置的原始步骤顺序',
  MODIFY COLUMN `step_name` varchar(100) NOT NULL COMMENT '提交时审批步骤名称',
  MODIFY COLUMN `approver_type` varchar(30) NOT NULL COMMENT '审批参与方式：role角色、user指定用户、dept_manager部门负责人',
  MODIFY COLUMN `role_id` bigint unsigned DEFAULT NULL COMMENT '角色ID，关联sys_role',
  MODIFY COLUMN `user_id` bigint unsigned DEFAULT NULL COMMENT '用户ID，关联sys_user',
  MODIFY COLUMN `dept_id` bigint unsigned DEFAULT NULL COMMENT '科室ID',
  MODIFY COLUMN `min_approvals` int unsigned NOT NULL DEFAULT '1' COMMENT '该节点最少通过人数',
  MODIFY COLUMN `allow_self_approve` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许提交人审批自己的申请：0否、1是',
  MODIFY COLUMN `data_scope` tinyint NOT NULL DEFAULT '1' COMMENT '数据权限范围：1全部、2本部门、3本部门及下级、4仅本人',
  MODIFY COLUMN `route_status` varchar(20) NOT NULL DEFAULT 'waiting' COMMENT '路由节点状态：waiting等待、pending待审批、completed已完成',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `complete_time` datetime DEFAULT NULL COMMENT '审批节点完成时间';

ALTER TABLE `product_guided_location`
  MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '记录主键';

ALTER TABLE `purchase_demand`
  MODIFY COLUMN `demand_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '采购需求ID，关联purchase_demand',
  MODIFY COLUMN `demand_no` varchar(50) NOT NULL COMMENT '采购需求单号',
  MODIFY COLUMN `demand_source` varchar(30) NOT NULL COMMENT '采购需求来源类型',
  MODIFY COLUMN `demand_status` varchar(30) NOT NULL DEFAULT 'draft' COMMENT '采购需求业务状态',
  MODIFY COLUMN `urgent_level` varchar(30) NOT NULL DEFAULT 'normal' COMMENT '采购需求紧急程度',
  MODIFY COLUMN `dept_id` bigint unsigned DEFAULT NULL COMMENT '科室ID',
  MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID，关联product',
  MODIFY COLUMN `quantity` decimal(18,4) NOT NULL COMMENT '业务数量',
  MODIFY COLUMN `approved_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '审核通过的需求数量',
  MODIFY COLUMN `suggested_purchase_qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '建议采购数量',
  MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  COMMENT = '采购需求记录';

ALTER TABLE `purchase_order`
  MODIFY COLUMN `purchase_type` varchar(30) DEFAULT NULL COMMENT '采购业务类型',
  MODIFY COLUMN `send_time` datetime DEFAULT NULL COMMENT '采购订单发送时间',
  MODIFY COLUMN `close_time` datetime DEFAULT NULL COMMENT '采购订单关闭时间',
  MODIFY COLUMN `close_reason` varchar(500) DEFAULT NULL COMMENT '采购订单关闭原因';

ALTER TABLE `purchase_order_tracking`
  MODIFY COLUMN `tracking_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '采购订单跟踪记录主键',
  MODIFY COLUMN `purchase_order_id` bigint unsigned NOT NULL COMMENT '采购订单ID，关联purchase_order',
  MODIFY COLUMN `event_type` varchar(50) NOT NULL COMMENT '业务事件类型编码',
  MODIFY COLUMN `event_status` varchar(50) NOT NULL COMMENT '采购订单跟踪事件状态',
  MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = '采购订单业务跟踪事件';

ALTER TABLE `purchase_plan`
  MODIFY COLUMN `plan_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '采购计划ID，关联purchase_plan',
  MODIFY COLUMN `plan_no` varchar(50) NOT NULL COMMENT '采购计划单号',
  MODIFY COLUMN `plan_status` varchar(30) NOT NULL DEFAULT 'draft' COMMENT '采购计划业务状态',
  MODIFY COLUMN `supplier_id` bigint unsigned NOT NULL COMMENT '供应商ID，关联supplier',
  MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID，关联product',
  MODIFY COLUMN `planned_quantity` decimal(18,4) NOT NULL COMMENT '采购计划数量',
  MODIFY COLUMN `converted_order_no` varchar(50) DEFAULT NULL COMMENT '转换生成的采购订单号',
  MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  COMMENT = '采购计划';

ALTER TABLE `purchase_plan_demand`
  MODIFY COLUMN `plan_id` bigint unsigned NOT NULL COMMENT '采购计划ID，关联purchase_plan',
  MODIFY COLUMN `demand_id` bigint unsigned NOT NULL COMMENT '采购需求ID，关联purchase_demand',
  MODIFY COLUMN `allocated_quantity` decimal(18,4) NOT NULL COMMENT '需求分配到采购计划的数量',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间';

ALTER TABLE `quota_package_event`
  MODIFY COLUMN `event_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '业务事件主键',
  MODIFY COLUMN `event_no` varchar(50) NOT NULL COMMENT '业务事件编号',
  MODIFY COLUMN `label_id` bigint unsigned DEFAULT NULL COMMENT '定数包标签ID，关联quota_package_label',
  MODIFY COLUMN `event_type` varchar(50) NOT NULL COMMENT '定数包业务事件类型编码',
  MODIFY COLUMN `status_before` varchar(30) DEFAULT NULL COMMENT '事件发生前的定数包标签状态',
  MODIFY COLUMN `status_after` varchar(30) DEFAULT NULL COMMENT '事件发生后的定数包标签状态',
  MODIFY COLUMN `qty_change` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '事件涉及的包内商品数量（商品最小单位），正负号按事件类型解释',
  MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `event_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '业务事件发生时间',
  COMMENT = '定数包业务事件流水';

ALTER TABLE `quota_package_label`
  MODIFY COLUMN `label_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '定数包标签ID，关联quota_package_label',
  MODIFY COLUMN `label_no` varchar(50) NOT NULL COMMENT '定数包标签编号',
  MODIFY COLUMN `trace_code_id` bigint unsigned DEFAULT NULL COMMENT '稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成',
  MODIFY COLUMN `task_id` bigint unsigned DEFAULT NULL COMMENT '来源打包任务ID，关联quota_packing_task',
  MODIFY COLUMN `template_id` bigint unsigned NOT NULL COMMENT '定数包模板ID，关联quota_package_template',
  MODIFY COLUMN `warehouse_id` bigint unsigned NOT NULL COMMENT '库房ID，关联warehouse',
  MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID，关联product',
  MODIFY COLUMN `package_quantity` decimal(18,4) NOT NULL COMMENT '每包所含商品数量（商品最小单位）',
  MODIFY COLUMN `status` varchar(30) NOT NULL DEFAULT 'available' COMMENT '标签状态：pending_print待打印、available可用、picked已拣配、signed已签收、consumed已消耗、settled已结算、void已作废',
  MODIFY COLUMN `version` int NOT NULL DEFAULT '1' COMMENT '并发控制版本号',
  MODIFY COLUMN `print_count` int NOT NULL DEFAULT '0' COMMENT '标签打印次数',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
  COMMENT = '定数包标签与生命周期状态';

ALTER TABLE `quota_package_label_source`
  MODIFY COLUMN `source_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '定数包标签来源明细主键',
  MODIFY COLUMN `label_id` bigint unsigned NOT NULL COMMENT '定数包标签ID，关联quota_package_label',
  MODIFY COLUMN `batch_id` bigint unsigned NOT NULL COMMENT '库存批次ID，关联inventory_batch',
  MODIFY COLUMN `source_qty` decimal(18,4) NOT NULL COMMENT '该来源批次分摊到本包的数量（商品最小单位）',
  MODIFY COLUMN `unit_price` decimal(18,4) NOT NULL COMMENT '采购单价',
  MODIFY COLUMN `warehouse_id` bigint unsigned NOT NULL COMMENT '库房ID，关联warehouse',
  COMMENT = '定数包标签来源库存批次及数量分摊';

ALTER TABLE `quota_packing_task`
  MODIFY COLUMN `task_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '业务任务主键或关联任务ID',
  MODIFY COLUMN `task_no` varchar(50) NOT NULL COMMENT '业务任务编号',
  MODIFY COLUMN `template_id` bigint unsigned NOT NULL COMMENT '定数包模板ID，关联quota_package_template',
  MODIFY COLUMN `warehouse_id` bigint unsigned NOT NULL COMMENT '库房ID，关联warehouse',
  MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID，关联product',
  MODIFY COLUMN `package_count` decimal(18,4) NOT NULL COMMENT '计划打包数量（包）',
  MODIFY COLUMN `package_quantity` decimal(18,4) NOT NULL COMMENT '每包商品数量（商品最小单位），提交时模板快照',
  MODIFY COLUMN `planned_loose_qty` decimal(18,4) NOT NULL COMMENT '计划使用散货数量（商品最小单位）',
  MODIFY COLUMN `reserved_loose_qty` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已预占散货数量（商品最小单位）',
  MODIFY COLUMN `status` varchar(30) NOT NULL DEFAULT 'pending_confirm' COMMENT '打包状态：pending_confirm待确认、need_recalculate待重算、confirmed已确认、cancelled已取消、terminated已终止',
  MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `confirm_time` datetime DEFAULT NULL COMMENT '打包确认时间',
  MODIFY COLUMN `cancel_time` datetime DEFAULT NULL COMMENT '取消或终止时间',
  COMMENT = '定数包打包任务';

ALTER TABLE `quota_packing_task_reservation`
  MODIFY COLUMN `reservation_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '打包任务库存预占明细主键',
  MODIFY COLUMN `task_id` bigint unsigned NOT NULL COMMENT '打包任务ID，关联quota_packing_task',
  MODIFY COLUMN `balance_id` bigint unsigned NOT NULL COMMENT '库存余额ID，关联inventory_balance',
  MODIFY COLUMN `batch_id` bigint unsigned NOT NULL COMMENT '库存批次ID，关联inventory_batch',
  MODIFY COLUMN `reserved_qty` decimal(18,4) NOT NULL COMMENT '该批次预占散货数量（商品最小单位）',
  MODIFY COLUMN `unit_price` decimal(18,4) NOT NULL COMMENT '采购单价',
  MODIFY COLUMN `status` varchar(30) NOT NULL DEFAULT 'reserved' COMMENT '预占状态：reserved已预占、consumed已转为打包消耗、released已释放、terminated已终止',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
  COMMENT = '定数包打包任务库存预占明细';

ALTER TABLE `recall_event`
  MODIFY COLUMN `recall_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '召回或隔离记录主键',
  MODIFY COLUMN `recall_no` varchar(50) NOT NULL COMMENT '召回或隔离单号',
  MODIFY COLUMN `warehouse_name` varchar(80) NOT NULL COMMENT '库房名称',
  MODIFY COLUMN `product_code` varchar(50) NOT NULL COMMENT '商品编码',
  MODIFY COLUMN `product_name` varchar(120) NOT NULL COMMENT '商品名称',
  MODIFY COLUMN `affected_qty` decimal(18,4) NOT NULL COMMENT '受影响商品数量（商品最小单位）',
  MODIFY COLUMN `status` varchar(30) NOT NULL COMMENT '召回或隔离执行状态，isolated表示库存已隔离',
  MODIFY COLUMN `reason` varchar(500) DEFAULT NULL COMMENT '业务原因',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = '商品批次召回或隔离记录';

ALTER TABLE `replenishment_analysis_requisition`
  MODIFY COLUMN `analysis_id` bigint unsigned NOT NULL COMMENT '科室智能补货分析ID，关联replenishment_smart_analysis',
  MODIFY COLUMN `requisition_id` bigint unsigned NOT NULL COMMENT '科室申领单ID，关联department_requisition',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间';

ALTER TABLE `replenishment_smart_analysis`
  MODIFY COLUMN `status` varchar(30) NOT NULL DEFAULT 'draft' COMMENT '分析状态：draft草稿、generated已生成申领';

ALTER TABLE `settlement_bill_item`
  MODIFY COLUMN `trace_code_id` bigint unsigned DEFAULT NULL COMMENT '稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成';

ALTER TABLE `shortage_replenishment_task`
  MODIFY COLUMN `task_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '业务任务主键或关联任务ID',
  MODIFY COLUMN `task_no` varchar(50) NOT NULL COMMENT '业务任务编号',
  MODIFY COLUMN `dept_name` varchar(80) NOT NULL COMMENT '科室名称',
  MODIFY COLUMN `product_code` varchar(50) NOT NULL COMMENT '商品编码',
  MODIFY COLUMN `product_name` varchar(120) NOT NULL COMMENT '商品名称',
  MODIFY COLUMN `min_qty` decimal(18,4) NOT NULL COMMENT '库存下限数量（商品最小单位）',
  MODIFY COLUMN `current_qty` decimal(18,4) NOT NULL COMMENT '当前库存数量（商品最小单位）',
  MODIFY COLUMN `replenish_qty` decimal(18,4) NOT NULL COMMENT '建议补货数量（商品最小单位）',
  MODIFY COLUMN `status` varchar(30) NOT NULL COMMENT '短缺补货任务状态，pending_replenish表示待补货',
  MODIFY COLUMN `source_type` varchar(50) DEFAULT NULL COMMENT '业务来源类型',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = '科室短缺补货任务';

ALTER TABLE `spd_delivery_batch`
  MODIFY COLUMN `delivery_id` bigint unsigned NOT NULL COMMENT '配送单ID，关联spd_delivery_order',
  MODIFY COLUMN `source_event_id` bigint unsigned NOT NULL COMMENT '来源库存扣减事件ID，关联inventory_event',
  MODIFY COLUMN `source_warehouse_id` bigint unsigned NOT NULL COMMENT '来源库房ID，关联warehouse',
  MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID，关联product',
  MODIFY COLUMN `batch_id` bigint unsigned NOT NULL COMMENT '库存批次ID，关联inventory_batch',
  MODIFY COLUMN `quantity` decimal(18,4) NOT NULL COMMENT '该配送单从来源批次扣减的数量（商品最小单位）',
  COMMENT = '配送单来源库存批次与扣减事件关联';

ALTER TABLE `spd_delivery_order`
  MODIFY COLUMN `delivery_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '配送单ID，关联spd_delivery_order',
  MODIFY COLUMN `delivery_no` varchar(50) NOT NULL COMMENT '配送单号',
  MODIFY COLUMN `requisition_no` varchar(50) DEFAULT NULL COMMENT '来源科室申领单号',
  MODIFY COLUMN `dept_name` varchar(80) NOT NULL COMMENT '科室名称',
  MODIFY COLUMN `warehouse_name` varchar(80) NOT NULL COMMENT '库房名称',
  MODIFY COLUMN `product_code` varchar(50) NOT NULL COMMENT '商品编码',
  MODIFY COLUMN `product_name` varchar(120) NOT NULL COMMENT '商品名称',
  MODIFY COLUMN `quantity` decimal(18,4) NOT NULL COMMENT '业务数量',
  MODIFY COLUMN `status` varchar(30) NOT NULL COMMENT '拣配配送单业务状态',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `sign_time` datetime DEFAULT NULL COMMENT '配送签收时间',
  COMMENT = 'SPD科室拣配配送单';

ALTER TABLE `spd_delivery_trace_code`
  MODIFY COLUMN `delivery_id` bigint unsigned NOT NULL COMMENT '配送单ID，关联spd_delivery_order',
  MODIFY COLUMN `trace_code_id` bigint unsigned NOT NULL COMMENT '稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  COMMENT = '配送单与收费耗材追溯单元关联';

ALTER TABLE `sys_field_option`
  MODIFY COLUMN `option_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '字段选项主键',
  MODIFY COLUMN `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示排序号',
  MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记：0未删除、1已删除',
  MODIFY COLUMN `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间';

ALTER TABLE `sys_role_dept`
  MODIFY COLUMN `role_id` bigint unsigned NOT NULL COMMENT '角色ID，关联sys_role',
  MODIFY COLUMN `dept_id` bigint unsigned NOT NULL COMMENT '科室ID',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间';

ALTER TABLE `sys_role_perm`
  MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '记录主键';

ALTER TABLE `sys_sequence`
  MODIFY COLUMN `seq_key` varchar(100) NOT NULL COMMENT '业务单号序列键，区分业务及编号周期',
  MODIFY COLUMN `seq_value` bigint unsigned NOT NULL DEFAULT '0' COMMENT '该序列当前已分配值',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
  COMMENT = '业务单号持久化序列';

ALTER TABLE `sys_user_role`
  MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '记录主键';

ALTER TABLE `sys_validation_rule`
  MODIFY COLUMN `rule_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '业务校验规则主键',
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT '1' COMMENT '启用状态：0停用、1启用',
  MODIFY COLUMN `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间';

ALTER TABLE `udi_trace_code`
  MODIFY COLUMN `trace_code_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成',
  MODIFY COLUMN `unique_code` varchar(120) NOT NULL COMMENT '业务唯一码文本',
  MODIFY COLUMN `trace_scope` varchar(40) NOT NULL DEFAULT 'high_value' COMMENT '追溯范围：high_value收费耗材、low_value_quota_pack低值定数包',
  MODIFY COLUMN `package_label_no` varchar(80) DEFAULT NULL COMMENT '定数包标签编号',
  MODIFY COLUMN `template_code` varchar(80) DEFAULT NULL COMMENT '定数包模板编码',
  MODIFY COLUMN `template_name` varchar(120) DEFAULT NULL COMMENT '定数包模板名称',
  MODIFY COLUMN `package_quantity` decimal(18,4) DEFAULT NULL COMMENT '每包所含商品数量（商品最小单位）',
  MODIFY COLUMN `package_unit` varchar(30) DEFAULT NULL COMMENT '定数包内商品计量单位',
  MODIFY COLUMN `package_status` varchar(40) DEFAULT NULL COMMENT '定数包标签业务状态',
  MODIFY COLUMN `product_code` varchar(64) NOT NULL COMMENT '商品编码',
  MODIFY COLUMN `product_name` varchar(255) NOT NULL COMMENT '商品名称',
  MODIFY COLUMN `spec_model` varchar(255) DEFAULT NULL COMMENT '规格型号',
  MODIFY COLUMN `manufacturer_name` varchar(255) DEFAULT NULL COMMENT '厂家名称',
  MODIFY COLUMN `supplier_name` varchar(255) DEFAULT NULL COMMENT '供应商名称',
  MODIFY COLUMN `batch_no` varchar(100) DEFAULT NULL COMMENT '批号展示文本',
  MODIFY COLUMN `expire_date` date DEFAULT NULL COMMENT '有效期截止日期',
  MODIFY COLUMN `current_location` varchar(255) DEFAULT NULL COMMENT '当前所在位置',
  MODIFY COLUMN `current_department` varchar(255) DEFAULT NULL COMMENT '当前所属科室',
  MODIFY COLUMN `current_status` varchar(40) NOT NULL DEFAULT 'in_stock' COMMENT '当前追溯业务状态',
  MODIFY COLUMN `responsible_person` varchar(80) DEFAULT NULL COMMENT '当前责任人',
  MODIFY COLUMN `patient_no` varchar(80) DEFAULT NULL COMMENT '患者编号',
  MODIFY COLUMN `patient_name_masked` varchar(80) DEFAULT NULL COMMENT '脱敏患者姓名',
  MODIFY COLUMN `risk_level` varchar(40) NOT NULL DEFAULT 'normal' COMMENT '追溯风险等级',
  MODIFY COLUMN `last_event_name` varchar(100) DEFAULT NULL COMMENT '最近追溯事件名称',
  MODIFY COLUMN `last_event_time` datetime DEFAULT NULL COMMENT '最近追溯事件时间',
  MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
  COMMENT = '收费耗材与低值定数包统一追溯主档';

ALTER TABLE `udi_trace_event`
  MODIFY COLUMN `trace_event_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '追溯事件主键',
  MODIFY COLUMN `trace_code_id` bigint unsigned NOT NULL COMMENT '稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成',
  MODIFY COLUMN `event_no` varchar(80) NOT NULL COMMENT '业务事件编号',
  MODIFY COLUMN `event_type` varchar(64) NOT NULL COMMENT '业务事件类型编码',
  MODIFY COLUMN `event_name` varchar(100) NOT NULL COMMENT '追溯事件名称',
  MODIFY COLUMN `biz_no` varchar(100) DEFAULT NULL COMMENT '关联业务单号',
  MODIFY COLUMN `location_name` varchar(255) DEFAULT NULL COMMENT '所在位置名称',
  MODIFY COLUMN `department_name` varchar(255) DEFAULT NULL COMMENT '科室名称',
  MODIFY COLUMN `operator_name` varchar(80) DEFAULT NULL COMMENT '操作人姓名或账号',
  MODIFY COLUMN `event_time` datetime NOT NULL COMMENT '业务事件发生时间',
  MODIFY COLUMN `status` varchar(40) NOT NULL DEFAULT 'done' COMMENT '追溯事件业务状态',
  MODIFY COLUMN `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  MODIFY COLUMN `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示排序号',
  COMMENT = 'UDI与唯一码追溯事件';

ALTER TABLE `udi_trace_patient_binding`
  MODIFY COLUMN `binding_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '患者绑定记录主键',
  MODIFY COLUMN `trace_code_id` bigint unsigned NOT NULL COMMENT '稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成',
  MODIFY COLUMN `patient_no` varchar(80) NOT NULL COMMENT '患者编号',
  MODIFY COLUMN `patient_name_masked` varchar(80) DEFAULT NULL COMMENT '脱敏患者姓名',
  MODIFY COLUMN `department_name` varchar(255) DEFAULT NULL COMMENT '科室名称',
  MODIFY COLUMN `location_name` varchar(255) DEFAULT NULL COMMENT '所在位置名称',
  MODIFY COLUMN `binding_status` varchar(30) NOT NULL DEFAULT 'bound' COMMENT '追溯单元患者绑定状态',
  MODIFY COLUMN `bind_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '患者绑定时间',
  COMMENT = '收费耗材追溯单元与患者绑定记录';

