# 后端数据库数据字典

以Flyway V87数据库结构为基准，覆盖业务表及框架迁移历史。字段说明同时存储于MySQL表和列注释；现有中文说明保留，补齐缺失说明并将英文说明改为中文。

“关联某表”表示业务引用含义，是否存在物理外键以建表约束为准。数量口径以字段说明和服务处理为准；业务快照用于查询或历史留痕。Flyway历史表由框架维护。

共92张表、1156个字段。本次补齐或中文化29条表描述、414条字段说明。

## 验证记录

2026-09-30：Flyway V87已应用到本地ISPD。92张表与1156个字段的注释缺失数均为0。隔离迁移前后全部表数据校验和一致，列定义、生成表达式、默认值、索引、外键及检查约束保持一致；历史查询表不存在时迁移也通过。后端回归678项，失败0、错误0、条件跳过35。

业务库复核结构仅注释变化。重启前后90张表数据校验和一致；Flyway历史正常增加V87记录，运营驾驶舱快照由既有启动任务刷新。证据：`output/database-comments-verification.json`、`output/database-comments-isolated.log`、`output/database-comments-backend-tests.log`。迁移前DDL备份：`output/database-schema-before-v87.sql`。

三张`inventory_*_stock`历史查询快照表的字段说明仅记录现存数据库结构，不代表新增、恢复或接入库存事件快照业务模块。

## 表索引

| 表名 | 业务说明 |
| --- | --- |
| `approval_flow` | 审批流配置表 |
| `approval_flow_step` | 审批流步骤配置表 |
| `audit_log` | 审计日志表 |
| `batch_price_adjustment` | 库存批次价格调整申请 |
| `campus` | 院区主数据 |
| `centralized_procurement_task` | 集采年度执行任务表 |
| `cold_chain_exception` | 冷链温度异常登记与处置 |
| `consumption_red_flush` | 科室消耗红冲记录 |
| `data_quality_issue` | 数据质量修复留痕 |
| `department_consumption` | 科室消耗单表 |
| `department_consumption_item` | 科室消耗明细表 |
| `department_requisition` | 科室申领单表 |
| `department_requisition_item` | 科室申领明细表 |
| `department_requisition_trace_code` | 科室申领单与收费耗材追溯单元关联 |
| `department_warehouse_catalog` | 科室库房申领目录范围 |
| `flyway_schema_history` | Flyway数据库迁移版本与执行历史（框架维护） |
| `high_value_charge` | SPD收费耗材使用记录（接收外部计费后记录使用及扣减库存） |
| `his_charge_detail` | HIS耗材收费明细回传表 |
| `inventory_balance` | 库存余额表 |
| `inventory_batch` | 系统库存批次表 |
| `inventory_batch_trace_code` | 收货库存批次与收费耗材追溯单元关联 |
| `inventory_daily_summary` | 全院物资每日进销存快照表 |
| `inventory_event` | 库存事件流水表 |
| `inventory_event_trace_code` | 不可变库存事件与收费耗材或定数包追溯身份关联 |
| `inventory_quota_package_stock` | 定数包库存固定快照表 |
| `inventory_stocktaking` | 库存盘点单 |
| `inventory_stocktaking_item` | 库存盘点单商品明细 |
| `inventory_trace_code_stock` | 唯一码及UDI库存固定快照表 |
| `inventory_warehouse_product_stock` | 库房商品库存固定快照表（最小单位口径） |
| `license_document` | 证照与合同表 |
| `license_revision` | 不可变证照变更与续证历史 |
| `manufacturer` | 厂家表 |
| `mobile_operation_receipt` | 移动端业务操作幂等回执 |
| `operation_cockpit_snapshot` | 运营驾驶舱月度展示快照 |
| `pda_offline_record` | PDA离线业务上传记录 |
| `pending_product_application` | 待审批商品目录申请表 |
| `pending_product_approval_action` | 待审批目录不可变审批动作 |
| `pending_product_approval_route_step` | 目录申请不可变审批路由快照 |
| `print_template` | 打印模板表 |
| `product` | 商品表 |
| `product_category` | 商品目录分类字段表 |
| `product_guided_location` | 散货引导货位表 |
| `purchase_demand` | 采购需求记录 |
| `purchase_order` | 采购订单表 |
| `purchase_order_item` | 采购订单明细表 |
| `purchase_order_tracking` | 采购订单业务跟踪事件 |
| `purchase_plan` | 采购计划 |
| `purchase_plan_demand` | 采购计划来源需求关系 |
| `purchase_replenishment_analysis` | 采购智能补货分析主表 |
| `purchase_replenishment_analysis_item` | 采购智能补货分析明细表 |
| `quota_package_event` | 定数包业务事件流水 |
| `quota_package_label` | 定数包标签与生命周期状态 |
| `quota_package_label_source` | 定数包标签来源库存批次及数量分摊 |
| `quota_package_template` | 定数包模板表 |
| `quota_package_template_item` | 定数包模板明细表 |
| `quota_packing_task` | 定数包打包任务 |
| `quota_packing_task_reservation` | 定数包打包任务库存预占明细 |
| `quota_safety_stock` | 定数安全量表 |
| `recall_event` | 商品批次召回或隔离记录 |
| `receiving_order` | 收货验收单表 |
| `receiving_order_item` | 收货验收明细表 |
| `replenishment_analysis_requisition` | 智能补货分析生成申领关联 |
| `replenishment_smart_analysis` | 智能补货分析主表 |
| `replenishment_smart_analysis_item` | 智能补货分析明细表 |
| `settlement_bill` | 结算单表 |
| `settlement_bill_item` | 结算单明细表 |
| `shortage_replenishment_task` | 科室短缺补货任务 |
| `spd_delivery_batch` | 配送单来源库存批次与扣减事件关联 |
| `spd_delivery_order` | SPD科室拣配配送单 |
| `spd_delivery_package_binding` | 拣配配送定数包绑定表 |
| `spd_delivery_quota_package` | 配送定数包分配表 |
| `spd_delivery_trace_code` | 配送单与收费耗材追溯单元关联 |
| `supplier` | 供应商表 |
| `sys_attachment` | 附件表 |
| `sys_dept` | 科室表 |
| `sys_field_option` | 表格下拉选字段选项字典 |
| `sys_login_log` | 登录日志表 |
| `sys_permission` | 权限表 |
| `sys_role` | 角色表 |
| `sys_role_dept` | 角色自定义数据权限部门 |
| `sys_role_perm` | 角色权限关系表 |
| `sys_sequence` | 业务单号持久化序列 |
| `sys_user` | 用户表 |
| `sys_user_role` | 用户角色关系表 |
| `sys_validation_rule` | 业务校验规则字段组合存储 |
| `system_config` | 系统配置表 |
| `udi_trace_code` | 收费耗材与低值定数包统一追溯主档 |
| `udi_trace_event` | UDI与唯一码追溯事件 |
| `udi_trace_patient_binding` | 收费耗材追溯单元与患者绑定记录 |
| `warehouse` | 库房表 |
| `warehouse_location` | 货位表 |
| `warehouse_product_binding` | 库房可管理商品绑定 |

## approval_flow

审批流配置表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| flow_id | bigint unsigned | 否 | 无 | 审批流ID；自增主键 |
| feature_code | varchar(80) | 否 | 无 | 功能编码 |
| feature_name | varchar(100) | 否 | 无 | 功能名称 |
| node_code | varchar(80) | 否 | 无 | 功能节点编码 |
| node_name | varchar(100) | 否 | 无 | 功能节点名称 |
| scope_type | varchar(30) | 否 | global | 适用范围：global/department/warehouse/role |
| scope_id | varchar(64) | 否 | default | 适用对象 |
| data_scope | tinyint | 否 | 1 | 数据隔离：1全部 2本部门 3本部门及下级 4本人 |
| status | tinyint | 否 | 1 | 状态：0停用 1启用 |
| remark | varchar(500) | 是 | NULL | 备注 |
| dept_id | bigint unsigned | 是 | NULL | 所属部门 |
| create_by | bigint unsigned | 是 | NULL | 创建人 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |
| revision_no | int | 否 | 1 | 审批流程当前配置版本；修改步骤后递增 |
| active_catalog_scope | varchar(191) | 是 | 生成列：(case when ((`feature_code` = _utf8mb4\'pending-product-catalog\') and (`node_code` = _utf8mb4\'initial-review\') and (`status` = 1) and (`deleted` = 0)) then concat(`scope_type`,_utf8mb4\':\',(case when (`scope_type` = _utf8mb4\'global\') then _utf8mb4\'default\' when (`scope_type` = _utf8mb4\'department\') then coalesce(cast(`dept_id` as char charset utf8mb4),`scope_id`) when (`scope_type` = _utf8mb4\'role\') then lower(replace(`scope_id`,_utf8mb4\'ROLE_\',_utf8mb4\'\')) else `scope_id` end)) else NULL end) | 启用目录顺序审批的归一化适用范围，用于防止同范围流程冲突 |
| non_catalog_scope_guard | tinyint | 是 | 生成列：(case when ((`feature_code` = _utf8mb4\'pending-product-catalog\') and (`node_code` = _utf8mb4\'initial-review\')) then NULL else 1 end) | 非目录流程唯一约束辅助列：目录顺序审批为NULL，其他流程为1 |

## approval_flow_step

审批流步骤配置表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| step_id | bigint unsigned | 否 | 无 | 审批步骤ID；自增主键 |
| flow_id | bigint unsigned | 否 | 无 | 审批流ID |
| step_order | int | 否 | 无 | 步骤序号 |
| step_name | varchar(100) | 否 | 无 | 步骤名称 |
| approver_type | varchar(30) | 否 | role | 审批人类型：role/user/dept_manager |
| role_id | bigint unsigned | 是 | NULL | 审批角色ID |
| user_id | bigint unsigned | 是 | NULL | 审批用户ID |
| dept_id | bigint unsigned | 是 | NULL | 审批部门ID |
| min_approvals | int | 否 | 1 | 最少通过人数 |
| allow_self_approve | tinyint | 否 | 0 | 是否允许自审 |
| data_scope | tinyint | 否 | 1 | 步骤数据隔离：1全部 2本部门 3本部门及下级 4本人 |
| status | tinyint | 否 | 1 | 状态：0停用 1启用 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| revision_no | int | 否 | 1 | 审批步骤所属配置版本；旧版本归档以保留历史引用 |

## audit_log

审计日志表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| audit_id | bigint unsigned | 否 | 无 | 审计日志ID；自增主键 |
| operator_id | bigint unsigned | 是 | NULL | 操作人 |
| operator_name | varchar(50) | 是 | NULL | 操作人名称 |
| operation_type | varchar(50) | 否 | 无 | 操作类型 |
| biz_type | varchar(50) | 否 | 无 | 业务类型 |
| biz_id | bigint unsigned | 是 | NULL | 业务ID |
| biz_no | varchar(64) | 是 | NULL | 业务单号 |
| before_data | json | 是 | NULL | 变更前数据 |
| after_data | json | 是 | NULL | 变更后数据 |
| ip_address | varchar(50) | 是 | NULL | IP地址 |
| operation_time | datetime | 否 | CURRENT_TIMESTAMP | 操作时间 |
| remark | varchar(500) | 是 | NULL | 备注 |

## batch_price_adjustment

库存批次价格调整申请

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| adjustment_id | bigint unsigned | 否 | 无 | 批次调价申请主键；自增主键 |
| adjustment_no | varchar(50) | 否 | 无 | 批次调价申请单号 |
| batch_id | bigint unsigned | 否 | 无 | 库存批次ID，关联inventory_batch |
| old_unit_price | decimal(18,4) | 否 | 无 | 调整前的采购单价 |
| new_unit_price | decimal(18,4) | 否 | 无 | 调整后的采购单价 |
| affected_qty | decimal(18,4) | 否 | 无 | 调价影响数量（商品最小单位） |
| reason | varchar(500) | 是 | NULL | 业务原因 |
| status | varchar(30) | 否 | draft | 批次调价审批状态 |
| approve_by | bigint unsigned | 是 | NULL | 审批人用户ID，关联sys_user |
| approve_time | datetime | 是 | NULL | 审批时间 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## campus

院区主数据

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| campus_id | bigint unsigned | 否 | 无 | 院区ID；自增主键 |
| campus_code | varchar(50) | 否 | 无 | 院区编码 |
| campus_name | varchar(80) | 否 | 无 | 院区名称 |
| address | varchar(200) | 是 | NULL | 院区地址 |
| manager_name | varchar(50) | 是 | NULL | 负责人 |
| phone | varchar(30) | 是 | NULL | 联系电话 |
| sort_order | int | 否 | 0 | 排序 |
| status | tinyint | 否 | 1 | 状态：0-停用 1-启用 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## centralized_procurement_task

集采年度执行任务表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| task_id | bigint unsigned | 否 | 无 | 集采任务ID；自增主键 |
| batch_code | varchar(80) | 否 | 无 | 集采批次编码 |
| batch_name | varchar(120) | 否 | 无 | 集采批次名称 |
| task_year | int | 否 | 无 | 任务年度 |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| selected_manufacturer_id | bigint unsigned | 是 | NULL | 中选厂家ID |
| selected_price | decimal(18,4) | 否 | 0.0000 | 中选价格 |
| annual_target_quantity | decimal(18,4) | 否 | 0.0000 | 年度任务量 |
| incomplete_reason | varchar(500) | 是 | NULL | 未完成原因 |
| status | tinyint | 否 | 1 | 状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |

## cold_chain_exception

冷链温度异常登记与处置

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| event_id | bigint unsigned | 否 | 无 | 冷链异常登记主键；自增主键 |
| event_no | varchar(50) | 否 | 无 | 冷链异常登记单号 |
| warehouse_name | varchar(80) | 否 | 无 | 库房名称 |
| product_code | varchar(50) | 否 | 无 | 商品编码 |
| product_name | varchar(120) | 否 | 无 | 商品名称 |
| temperature | decimal(10,2) | 否 | 无 | 异常登记温度（摄氏度） |
| severity | varchar(30) | 否 | 无 | 冷链异常严重程度 |
| status | varchar(30) | 否 | 无 | 冷链异常处置状态，pending_dispose表示待处置 |
| event_time | datetime | 否 | CURRENT_TIMESTAMP | 业务事件发生时间 |

## consumption_red_flush

科室消耗红冲记录

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| flush_id | bigint unsigned | 否 | 无 | 科室消耗红冲记录主键；自增主键 |
| flush_no | varchar(50) | 否 | 无 | 科室消耗红冲单号 |
| source_consumption_no | varchar(50) | 否 | 无 | 被红冲的原科室消耗单号 |
| flush_type | varchar(50) | 否 | 无 | 红冲业务类型 |
| status | varchar(30) | 否 | 无 | 科室消耗红冲审批状态 |
| remark | varchar(500) | 是 | NULL | 备注 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## data_quality_issue

数据质量修复留痕

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| issue_id | bigint unsigned | 否 | 无 | 数据质量问题ID；自增主键 |
| source_table | varchar(80) | 否 | 无 | 来源表 |
| source_key | varchar(120) | 否 | 无 | 稳定业务键 |
| issue_type | varchar(50) | 否 | 无 | 问题类型 |
| original_data | json | 否 | 无 | 修复前快照 |
| resolution_status | varchar(30) | 否 | resolved | 数据质量问题处理状态：resolved已修复、reimport_required需重新导入 |
| resolution_note | varchar(500) | 否 | 无 | 处理说明 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录时间 |

## department_consumption

科室消耗单表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| consumption_id | bigint unsigned | 否 | 无 | 消耗单ID；自增主键 |
| consumption_no | varchar(50) | 否 | 无 | 消耗单号 |
| dept_id | bigint unsigned | 否 | 无 | 消耗科室 |
| warehouse_id | bigint unsigned | 是 | NULL | 实际消耗库房 |
| consumption_type | varchar(30) | 否 | 无 | 消耗类型 |
| related_biz_type | varchar(50) | 是 | NULL | 关联单据类型 |
| related_biz_id | bigint unsigned | 是 | NULL | 关联单据ID |
| patient_info | json | 是 | NULL | 患者信息 |
| status | varchar(30) | 否 | pending_confirm | 状态 |
| consume_by | bigint unsigned | 是 | NULL | 消耗人 |
| consume_time | datetime | 否 | CURRENT_TIMESTAMP | 消耗时间 |

## department_consumption_item

科室消耗明细表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| item_id | bigint unsigned | 否 | 无 | 明细ID；自增主键 |
| consumption_id | bigint unsigned | 否 | 无 | 消耗单ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| batch_id | bigint unsigned | 是 | NULL | 系统批次ID |
| quota_label_id | bigint unsigned | 是 | NULL | 定数包标签ID |
| trace_code_id | bigint unsigned | 是 | NULL | 稳定追溯码身份 |
| quantity | decimal(18,4) | 否 | 无 | 数量 |
| unit_price | decimal(18,4) | 否 | 无 | 消耗事实单价 |
| amount | decimal(18,4) | 否 | 无 | 金额 |

## department_requisition

科室申领单表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| requisition_id | bigint unsigned | 否 | 无 | 申领单ID；自增主键 |
| requisition_no | varchar(50) | 否 | 无 | 申领单号 |
| dept_id | bigint unsigned | 否 | 无 | 申领科室 |
| warehouse_id | bigint unsigned | 是 | NULL | 申领库房 |
| source_warehouse_id | bigint unsigned | 是 | NULL | 供应中心/一级库房ID |
| requisition_type | varchar(30) | 否 | 无 | 申领类型 |
| expected_arrival_date | date | 是 | NULL | 期望到货 |
| status | varchar(30) | 否 | draft | 状态 |
| applicant_id | bigint unsigned | 是 | NULL | 申领人 |
| apply_time | datetime | 否 | CURRENT_TIMESTAMP | 申领时间 |
| approve_by | bigint unsigned | 是 | NULL | 审批人 |
| approve_time | datetime | 是 | NULL | 审批时间 |

## department_requisition_item

科室申领明细表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| item_id | bigint unsigned | 否 | 无 | 明细ID；自增主键 |
| requisition_id | bigint unsigned | 否 | 无 | 申领单ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| quantity | decimal(18,4) | 否 | 无 | 申领数量 |
| item_type | varchar(30) | 是 | NULL | 申请明细类型: unique_code/quota_package/loose |
| quota_template_id | bigint unsigned | 是 | NULL | 定数包模板版本ID |
| quota_template_version | int | 是 | NULL | 定数包模板版本快照 |
| quota_package_quantity | decimal(18,4) | 是 | NULL | 包内基础数量快照 |
| quota_package_unit | varchar(20) | 是 | NULL | 包装单位快照 |
| requested_package_count | decimal(18,4) | 是 | NULL | 申请包数快照 |
| picked_quantity | decimal(18,4) | 否 | 0.0000 | 累计拣配基础数量 |
| unit | varchar(20) | 否 | 无 | 单位 |
| unit_price | decimal(18,4) | 否 | 0.0000 | 申请单价 |
| amount | decimal(18,4) | 否 | 0.0000 | 申请金额 |
| remark | varchar(300) | 是 | NULL | 备注 |

## department_requisition_trace_code

科室申领单与收费耗材追溯单元关联

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| requisition_id | bigint unsigned | 否 | 无 | 科室申领单ID，关联department_requisition |
| requisition_item_id | bigint unsigned | 否 | 无 | 科室申领明细ID，关联department_requisition_item |
| trace_code_id | bigint unsigned | 否 | 无 | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## department_warehouse_catalog

科室库房申领目录范围

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| catalog_id | bigint unsigned | 否 | 无 | 科室库房目录ID；自增主键 |
| dept_id | bigint unsigned | 否 | 无 | 科室ID |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| source_type | varchar(30) | 否 | manual | 来源：manual/inventory/quota_template/safety_stock |
| status | tinyint | 否 | 1 | 状态：0-停用 1-启用 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## flyway_schema_history

Flyway数据库迁移版本与执行历史（框架维护）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| installed_rank | int | 否 | 无 | 迁移安装顺序主键 |
| version | varchar(50) | 是 | NULL | 并发控制版本号 |
| description | varchar(200) | 否 | 无 | 迁移版本说明 |
| type | varchar(20) | 否 | 无 | 迁移类型 |
| script | varchar(1000) | 否 | 无 | 迁移脚本文件名 |
| checksum | int | 是 | NULL | 迁移脚本校验和 |
| installed_by | varchar(100) | 否 | 无 | 执行迁移的数据库用户 |
| installed_on | timestamp | 否 | CURRENT_TIMESTAMP | 迁移安装时间 |
| execution_time | int | 否 | 无 | 迁移执行耗时（毫秒） |
| success | tinyint(1) | 否 | 无 | 迁移是否成功：0失败、1成功 |

## high_value_charge

SPD收费耗材使用记录（接收外部计费后记录使用及扣减库存）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| charge_id | bigint unsigned | 否 | 无 | SPD收费耗材记录主键；自增主键 |
| charge_no | varchar(50) | 否 | 无 | SPD收费耗材记录业务编号 |
| source_system | varchar(40) | 是 | NULL | 来源系统 |
| external_charge_no | varchar(100) | 是 | NULL | 外部计费号 |
| operation_no | varchar(100) | 是 | NULL | 手术/治疗单号 |
| udi_code | varchar(120) | 是 | NULL | UDI标识文本 |
| unique_code | varchar(120) | 是 | NULL | 唯一码 |
| trace_code_id | bigint unsigned | 是 | NULL | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成 |
| dept_name | varchar(80) | 否 | 无 | 科室名称 |
| patient_no | varchar(50) | 否 | 无 | 患者编号 |
| product_code | varchar(50) | 否 | 无 | 商品编码 |
| product_name | varchar(120) | 否 | 无 | 商品名称 |
| quantity | decimal(18,4) | 否 | 无 | 业务数量 |
| amount | decimal(18,4) | 否 | 无 | 金额 |
| status | varchar(30) | 否 | 无 | 收费耗材计费处理状态 |
| charge_time | datetime | 是 | NULL | 计费时间 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## his_charge_detail

HIS耗材收费明细回传表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| charge_detail_id | bigint unsigned | 否 | 无 | HIS收费明细ID；自增主键 |
| external_charge_no | varchar(100) | 否 | 无 | HIS收费明细号 |
| source_system | varchar(40) | 否 | HIS | 来源系统 |
| dept_name | varchar(80) | 否 | 无 | 收费科室 |
| patient_no | varchar(50) | 否 | 无 | 住院号/患者编号 |
| patient_name_masked | varchar(80) | 是 | NULL | 患者脱敏姓名 |
| product_code | varchar(50) | 否 | 无 | SPD耗材编码 |
| medical_insurance_code | varchar(80) | 是 | NULL | HIS回传医保编码 |
| product_name | varchar(120) | 否 | 无 | HIS回传耗材名称 |
| quantity | decimal(18,4) | 否 | 无 | 计费数量 |
| unit_price | decimal(18,4) | 否 | 0.0000 | 收费单价 |
| amount | decimal(18,4) | 否 | 0.0000 | 收费金额 |
| charge_time | datetime | 否 | 无 | 收费时间 |
| status | varchar(30) | 否 | charged | 收费状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 接收时间 |

## inventory_balance

库存余额表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| balance_id | bigint unsigned | 否 | 无 | 库存余额ID；自增主键 |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID |
| location_id | bigint unsigned | 是 | NULL | 货位ID |
| location_key | bigint unsigned | 是 | 生成列：coalesce(`location_id`,0) | 归一化货位唯一键：无货位时为0，用于库存余额唯一约束 |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| batch_id | bigint unsigned | 否 | 无 | 系统批次ID |
| available_qty | decimal(18,4) | 否 | 0.0000 | 可用数量 |
| locked_qty | decimal(18,4) | 否 | 0.0000 | 锁定数量 |
| in_transit_qty | decimal(18,4) | 否 | 0.0000 | 在途数量 |
| isolated_qty | decimal(18,4) | 否 | 0.0000 | 隔离数量 |
| last_event_id | bigint unsigned | 是 | NULL | 最后事件ID |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |

## inventory_batch

系统库存批次表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| batch_id | bigint unsigned | 否 | 无 | 系统批次ID；自增主键 |
| system_batch_no | varchar(50) | 否 | 无 | 系统批次号 |
| receiving_order_id | bigint unsigned | 是 | NULL | 收货单ID |
| receiving_item_id | bigint unsigned | 是 | NULL | 收货明细ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| supplier_id | bigint unsigned | 是 | NULL | 供应商ID |
| production_batch_no | varchar(50) | 是 | NULL | 生产批号 |
| production_date | date | 是 | NULL | 生产日期 |
| expire_date | date | 是 | NULL | 有效期至 |
| batch_unit_price | decimal(18,4) | 否 | 无 | 批次单价 |
| ownership_type | varchar(30) | 否 | hospital_owned | 所有权类型 |
| settlement_mode | varchar(30) | 否 | purchase_in | 结算模式 |
| status | tinyint | 否 | 1 | 状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |

## inventory_batch_trace_code

收货库存批次与收费耗材追溯单元关联

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| batch_id | bigint unsigned | 否 | 无 | 库存批次ID，关联inventory_batch |
| trace_code_id | bigint unsigned | 否 | 无 | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成 |
| receiving_item_id | bigint unsigned | 否 | 无 | 收货验收明细ID，关联receiving_order_item |
| current_warehouse_id | bigint unsigned | 是 | NULL | 追溯单元当前库房ID，关联warehouse |
| lifecycle_status | varchar(40) | 否 | in_stock | 追溯单元生命周期状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## inventory_daily_summary

全院物资每日进销存快照表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| summary_id | bigint unsigned | 否 | 无 | 日报快照ID；自增主键 |
| business_date | date | 否 | 无 | 业务日期 |
| snapshot_time | datetime | 否 | 无 | 快照生成时点（次日00:00:00） |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| batch_id | bigint unsigned | 否 | 无 | 库存批次ID |
| supplier_id | bigint unsigned | 是 | NULL | 批次入库配送商ID |
| opening_quantity | decimal(18,4) | 否 | 0.0000 | 期初库存 |
| inbound_quantity | decimal(18,4) | 否 | 0.0000 | 本期入库 |
| return_quantity | decimal(18,4) | 否 | 0.0000 | 本期退库 |
| requisition_quantity | decimal(18,4) | 否 | 0.0000 | 本期领用 |
| consumption_quantity | decimal(18,4) | 否 | 0.0000 | 本期消耗 |
| scrap_quantity | decimal(18,4) | 否 | 0.0000 | 本期报废 |
| closing_quantity | decimal(18,4) | 否 | 0.0000 | 期末库存 |
| unit_price | decimal(18,4) | 否 | 0.0000 | 批次入库单价 |
| calculation_version | smallint unsigned | 否 | 1 | 日报计算口径版本，用于触发历史快照顺序回算 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |

## inventory_event

库存事件流水表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| event_id | bigint unsigned | 否 | 无 | 库存事件ID；自增主键 |
| event_no | varchar(50) | 否 | 无 | 事件编号 |
| event_type | varchar(50) | 否 | 无 | 事件类型 |
| transaction_type_code | varchar(40) | 是 | NULL | 稳定的库存账务交易类型编码 |
| event_category | varchar(20) | 否 | quantity | 事件类别：quantity数量变动、valuation价值变动 |
| source_biz_type | varchar(50) | 是 | NULL | 来源业务类型 |
| source_biz_id | bigint unsigned | 是 | NULL | 来源业务ID |
| dept_id_snapshot | bigint unsigned | 是 | NULL | 事件发生时科室ID快照 |
| dept_name_snapshot | varchar(100) | 是 | NULL | 事件发生时科室名称快照 |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID |
| warehouse_code_snapshot | varchar(50) | 是 | NULL | 事件发生时库房编码快照 |
| warehouse_name_snapshot | varchar(100) | 是 | NULL | 事件发生时库房名称快照 |
| location_id | bigint unsigned | 是 | NULL | 货位ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| product_code_snapshot | varchar(64) | 是 | NULL | 事件发生时商品编码快照 |
| product_name_snapshot | varchar(255) | 是 | NULL | 事件发生时商品名称快照 |
| spec_model_snapshot | varchar(255) | 是 | NULL | 事件发生时规格型号快照 |
| registration_no_snapshot | varchar(120) | 是 | NULL | 事件发生时注册证号快照 |
| unit_snapshot | varchar(30) | 是 | NULL | 事件发生时商品计量单位快照 |
| manufacturer_name_snapshot | varchar(255) | 是 | NULL | 事件发生时厂家名称快照 |
| supplier_id_snapshot | bigint unsigned | 是 | NULL | 事件发生时供应商ID，关联supplier快照 |
| supplier_name_snapshot | varchar(255) | 是 | NULL | 事件发生时供应商名称快照 |
| batch_id | bigint unsigned | 否 | 无 | 系统批次ID |
| system_batch_no_snapshot | varchar(50) | 是 | NULL | 事件发生时系统库存批次号快照 |
| production_batch_no_snapshot | varchar(50) | 是 | NULL | 事件发生时生产批号快照 |
| qty_change | decimal(18,4) | 否 | 无 | 数量变化 |
| qty_after | decimal(18,4) | 否 | 无 | 变化后数量 |
| unit_price_snapshot | decimal(18,4) | 是 | NULL | 事件发生时采购单价快照 |
| amount_snapshot | decimal(18,4) | 是 | NULL | 事件发生时带正负号的金额快照 |
| old_unit_price | decimal(18,4) | 是 | NULL | 调整前的采购单价 |
| new_unit_price | decimal(18,4) | 是 | NULL | 调整后的采购单价 |
| affected_qty_snapshot | decimal(18,4) | 是 | NULL | 价值变动事件影响的商品数量快照 |
| value_change | decimal(18,4) | 是 | NULL | 调价价值变化金额，正数增加、负数减少 |
| snapshot_origin | varchar(30) | 否 | legacy_backfill | 事件快照来源：captured发生时采集、legacy_backfill历史回填 |
| operator_id | bigint unsigned | 是 | NULL | 操作人 |
| event_time | datetime | 否 | CURRENT_TIMESTAMP | 事件时间 |
| remark | varchar(500) | 是 | NULL | 备注 |

## inventory_event_trace_code

不可变库存事件与收费耗材或定数包追溯身份关联

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| event_id | bigint unsigned | 否 | 无 | 不可变库存事件ID，关联inventory_event |
| trace_code_id | bigint unsigned | 否 | 无 | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成 |
| trace_type | varchar(40) | 否 | 无 | 追溯关联类型：high_value_unit收费耗材单元、quota_package定数包 |
| linked_quantity | decimal(18,4) | 否 | 1.0000 | 该追溯身份代表的商品数量 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## inventory_quota_package_stock

定数包库存固定快照表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| label_id | bigint unsigned | 否 | 无 | 定数包标签ID，关联quota_package_label |
| package_code | varchar(100) | 否 | 无 | 定数包标签编号 |
| status | varchar(30) | 否 | 无 | 来源定数包标签业务状态 |
| inventory_state | varchar(30) | 否 | 无 | 库存展示状态 |
| template_code | varchar(160) | 否 | 无 | 定数包模板编码 |
| template_name | varchar(200) | 否 | 无 | 定数包模板名称 |
| warehouse_name | varchar(100) | 否 | 无 | 库房名称 |
| product_code | varchar(80) | 否 | 无 | 商品编码 |
| product_name | varchar(200) | 否 | 无 | 商品名称 |
| spec_model | varchar(200) | 是 | NULL | 规格型号 |
| unit | varchar(30) | 是 | NULL | 商品计量单位 |
| package_quantity | decimal(18,4) | 否 | 无 | 该定数包所含商品数量（商品最小单位） |
| source_batches | text | 是 | NULL | 定数包来源批次展示文本 |
| source_update_time | datetime | 是 | NULL | 源业务记录最近更新时间 |
| refresh_time | datetime | 否 | CURRENT_TIMESTAMP | 库存查询快照刷新时间 |

## inventory_stocktaking

库存盘点单

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| stocktaking_id | bigint unsigned | 否 | 无 | 盘点单ID，关联inventory_stocktaking；自增主键 |
| stocktaking_no | varchar(50) | 否 | 无 | 盘点单号 |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID，关联warehouse |
| dept_name | varchar(80) | 是 | NULL | 盘点科室 |
| stocktaking_type | varchar(30) | 否 | 无 | 盘点业务类型 |
| status | varchar(30) | 否 | draft | 盘点单审批与执行状态 |
| reason | varchar(500) | 是 | NULL | 业务原因 |
| approve_by | bigint unsigned | 是 | NULL | 审批人用户ID，关联sys_user |
| approve_time | datetime | 是 | NULL | 审批时间 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## inventory_stocktaking_item

库存盘点单商品明细

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| item_id | bigint unsigned | 否 | 无 | 业务明细主键；自增主键 |
| stocktaking_id | bigint unsigned | 否 | 无 | 盘点单ID，关联inventory_stocktaking |
| balance_id | bigint unsigned | 是 | NULL | 库存余额ID（范围盘点按商品汇总为空） |
| product_id | bigint unsigned | 否 | 无 | 商品ID，关联product |
| batch_id | bigint unsigned | 是 | NULL | 系统批次ID（范围盘点按商品汇总为空） |
| system_qty | decimal(18,4) | 否 | 无 | 盘点时账面库存数量（商品最小单位） |
| actual_qty | decimal(18,4) | 是 | NULL | 实际数量 |
| diff_qty | decimal(18,4) | 是 | NULL | 差异数量 |
| diff_reason | varchar(500) | 是 | NULL | 盘点差异原因 |

## inventory_trace_code_stock

唯一码及UDI库存固定快照表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| trace_code_id | bigint unsigned | 否 | 无 | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成 |
| unique_code | varchar(120) | 否 | 无 | 业务唯一码文本 |
| udi_code | varchar(255) | 是 | NULL | UDI标识文本 |
| warehouse_name | varchar(100) | 是 | NULL | 库房名称 |
| product_code | varchar(80) | 否 | 无 | 商品编码 |
| product_name | varchar(200) | 否 | 无 | 商品名称 |
| spec_model | varchar(200) | 是 | NULL | 规格型号 |
| system_batch_no | varchar(100) | 是 | NULL | 系统库存批次号 |
| production_batch_no | varchar(100) | 是 | NULL | 生产批号 |
| expire_date | date | 是 | NULL | 有效期截止日期 |
| status | varchar(30) | 否 | 无 | 来源追溯码业务状态 |
| inventory_state | varchar(30) | 否 | 无 | 库存展示状态 |
| source_update_time | datetime | 是 | NULL | 源业务记录最近更新时间 |
| refresh_time | datetime | 否 | CURRENT_TIMESTAMP | 库存查询快照刷新时间 |

## inventory_warehouse_product_stock

库房商品库存固定快照表（最小单位口径）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID，关联warehouse |
| product_id | bigint unsigned | 否 | 无 | 商品ID，关联product |
| unit_price | decimal(18,4) | 否 | 无 | 采购单价 |
| warehouse_name | varchar(100) | 否 | 无 | 库房名称 |
| product_code | varchar(80) | 否 | 无 | 商品编码 |
| product_name | varchar(200) | 否 | 无 | 商品名称 |
| spec_model | varchar(200) | 是 | NULL | 规格型号 |
| unit | varchar(30) | 是 | NULL | 商品计量单位 |
| batch_count | int | 否 | 0 | 参与聚合的库存批次数 |
| available_qty | decimal(18,4) | 否 | 0.0000 | 可用库存数量（商品最小单位） |
| locked_qty | decimal(18,4) | 否 | 0.0000 | 锁定库存数量（商品最小单位） |
| in_transit_qty | decimal(18,4) | 否 | 0.0000 | 在途库存数量（商品最小单位） |
| isolated_qty | decimal(18,4) | 否 | 0.0000 | 隔离库存数量（商品最小单位） |
| package_loose_qty | decimal(18,4) | 否 | 0.0000 | 定数包内商品数量（折算商品最小单位） |
| total_qty | decimal(18,4) | 否 | 0.0000 | 库存合计数量（商品最小单位） |
| refresh_time | datetime | 否 | CURRENT_TIMESTAMP | 库存查询快照刷新时间 |

## license_document

证照与合同表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| license_id | bigint unsigned | 否 | 无 | 证照ID；自增主键 |
| license_type | varchar(30) | 否 | 无 | 证照类型: product/supplier/manufacturer/contract |
| license_name | varchar(150) | 否 | 无 | 证照名称 |
| license_no | varchar(150) | 是 | NULL | 证照编号 |
| owner_type | varchar(30) | 是 | NULL | 所属对象类型 |
| owner_id | bigint unsigned | 是 | NULL | 所属对象ID |
| owner_code | varchar(100) | 是 | NULL | 所属对象编码 |
| owner_name | varchar(150) | 是 | NULL | 所属对象名称 |
| party_a | varchar(150) | 是 | NULL | 甲方（合同） |
| party_b | varchar(150) | 是 | NULL | 乙方（合同） |
| contract_amount | decimal(18,2) | 是 | NULL | 合同金额 |
| issue_date | date | 是 | NULL | 签发日期 |
| expire_date | date | 是 | NULL | 有效期至 |
| status | tinyint | 否 | 1 | 状态: 1有效 0失效 |
| remark | varchar(500) | 是 | NULL | 备注 |
| create_by | bigint unsigned | 是 | NULL | 创建人 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |
| revision_no | int | 否 | 1 | 当前证照版本 |

## license_revision

不可变证照变更与续证历史

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| revision_id | bigint unsigned | 否 | 无 | 证照历史版本主键；自增主键 |
| license_id | bigint unsigned | 否 | 无 | 证照ID，关联license_document |
| revision_no | int | 否 | 无 | 该证照历史版本号，与证照ID共同唯一 |
| operation_type | varchar(30) | 否 | 无 | 证照历史操作类型：create新增、update修改、renew续证 |
| snapshot_json | json | 否 | 无 | 证照该版本的完整业务快照（JSON） |
| operator_id | bigint unsigned | 是 | NULL | 操作人用户ID，关联sys_user |
| operator_name | varchar(100) | 是 | NULL | 操作人姓名或账号 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## manufacturer

厂家表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| manufacturer_id | bigint unsigned | 否 | 无 | 厂家ID；自增主键 |
| manufacturer_code | varchar(50) | 否 | 无 | 厂家编码 |
| manufacturer_name | varchar(100) | 否 | 无 | 厂家名称 |
| credit_code | varchar(18) | 是 | NULL | 统一社会信用代码 |
| license_no | varchar(100) | 是 | NULL | 生产许可证号 |
| contact_name | varchar(50) | 是 | NULL | 联系人 |
| contact_phone | varchar(30) | 是 | NULL | 联系电话 |
| address | varchar(200) | 是 | NULL | 地址 |
| status | tinyint | 否 | 1 | 状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## mobile_operation_receipt

移动端业务操作幂等回执

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | bigint unsigned | 否 | 无 | 用户ID，关联sys_user |
| operation_id | char(36) | 否 | 无 | 客户端操作UUID，与用户ID共同构成幂等键 |
| device_id | varchar(128) | 否 | 无 | 移动端设备标识 |
| kind | varchar(32) | 否 | 无 | 移动端操作类型编码 |
| task_id | bigint unsigned | 否 | 无 | 操作关联业务任务ID，按kind区分对应业务 |
| dept_id | bigint unsigned | 否 | 无 | 科室ID |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID，关联warehouse |
| payload_hash | char(64) | 否 | 无 | 操作载荷SHA-256摘要，用于检查同一幂等键的请求一致性 |
| status | varchar(16) | 否 | 无 | 幂等回执状态：pending处理中、succeeded已成功 |
| result_json | json | 是 | NULL | 移动端操作处理结果（JSON），用于幂等重试返回 |
| created_at | datetime(6) | 否 | CURRENT_TIMESTAMP(6) | 移动端操作回执创建时间 |
| completed_at | datetime(6) | 是 | NULL | 移动端操作完成时间 |

## operation_cockpit_snapshot

运营驾驶舱月度展示快照

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| snapshot_id | bigint unsigned | 否 | 无 | 快照ID；自增主键 |
| statistics_month | date | 否 | 无 | 统计月份（月份首日） |
| statistics_date | date | 否 | 无 | 统计任务执行日期 |
| current_amount | decimal(18,4) | 否 | 0.0000 | 本月消耗金额 |
| previous_amount | decimal(18,4) | 否 | 0.0000 | 上月消耗金额 |
| month_on_month | decimal(18,2) | 否 | 0.00 | 金额环比百分比 |
| department_count | int | 否 | 0 | 本月消耗科室数 |
| warning_count | int | 否 | 0 | 重点监控预警数 |
| categories_json | json | 否 | 无 | 耗材分类对比 |
| trend_json | json | 否 | 无 | 近十二个月趋势 |
| departments_json | json | 否 | 无 | 科室消耗排名 |
| focused_products_json | json | 否 | 无 | 重点监控耗材排名 |
| alerts_json | json | 否 | 无 | 库存与异常提醒 |
| generated_at | datetime | 否 | CURRENT_TIMESTAMP | 快照生成时间 |

## pda_offline_record

PDA离线业务上传记录

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| record_id | bigint unsigned | 否 | 无 | PDA离线上传记录主键；自增主键 |
| record_no | varchar(50) | 否 | 无 | PDA离线上传记录编号 |
| client_record_no | varchar(80) | 是 | NULL | PDA端离线记录唯一号 |
| device_no | varchar(50) | 否 | 无 | PDA设备编号 |
| dept_id | bigint unsigned | 是 | NULL | 离线业务归属科室 |
| operation_type | varchar(50) | 否 | 无 | 业务操作类型 |
| upload_by | bigint unsigned | 是 | NULL | 上传操作人 |
| payload | json | 否 | 无 | PDA离线操作原始业务数据（JSON） |
| result_payload | json | 是 | NULL | 业务回放结果 |
| error_message | varchar(500) | 是 | NULL | 回放失败原因 |
| status | varchar(30) | 否 | 无 | 离线数据回放处理状态，replayed表示已回放 |
| upload_time | datetime | 否 | CURRENT_TIMESTAMP | PDA离线数据上传时间 |

## pending_product_application

待审批商品目录申请表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| application_id | bigint unsigned | 否 | 无 | 申请ID；自增主键 |
| application_no | varchar(50) | 否 | 无 | 申请单号 |
| application_type | varchar(30) | 否 | 无 | 申请类型 |
| product_id | bigint unsigned | 是 | NULL | 关联商品ID |
| supplier_id | bigint unsigned | 是 | NULL | 提交供应商ID |
| supplier_name | varchar(100) | 是 | NULL | 供应商名称 |
| product_name | varchar(100) | 是 | NULL | 商品名称 |
| product_code | varchar(50) | 是 | NULL | 商品编码 |
| spec_model | varchar(100) | 是 | NULL | 规格型号 |
| brand | varchar(50) | 是 | NULL | 品牌 |
| manufacturer_id | bigint unsigned | 是 | NULL | 生产厂家ID |
| manufacturer_name | varchar(100) | 是 | NULL | 生产厂家名称 |
| category_id | bigint unsigned | 是 | NULL | 商品分类ID |
| unit | varchar(20) | 是 | NULL | 基本单位 |
| purchase_price | decimal(18,4) | 是 | NULL | 采购价 |
| retail_price | decimal(18,4) | 是 | NULL | 零售价 |
| min_purchase_qty | decimal(18,4) | 是 | NULL | 最小采购量 |
| purchase_unit | varchar(20) | 是 | NULL | 采购单位 |
| conversion_rate | decimal(18,6) | 是 | NULL | 换算系数 |
| purchase_package_qty | decimal(18,3) | 是 | NULL | 采购包装数量 |
| udi_code | varchar(100) | 是 | NULL | UDI编码 |
| registration_no | varchar(100) | 是 | NULL | 注册证号 |
| registration_expire_date | date | 是 | NULL | 注册证有效期 |
| production_license_no | varchar(100) | 是 | NULL | 生产许可证号 |
| business_license_no | varchar(100) | 是 | NULL | 经营许可证号 |
| qualification_attachment_count | int | 否 | 0 | 资质附件数量 |
| is_high_value | tinyint | 否 | 0 | 是否高值耗材 |
| is_cold_chain | tinyint | 否 | 0 | 是否冷链 |
| is_quota_managed | tinyint | 否 | 0 | 是否定数管理 |
| is_key_monitored | tinyint | 否 | 0 | 是否重点监控 |
| storage_condition | varchar(30) | 是 | NULL | 储存条件 |
| product_snapshot | json | 否 | 无 | 商品申请快照 |
| change_diff | json | 是 | NULL | 变更差异 |
| approval_status | varchar(30) | 否 | pending_initial | 审批状态 |
| current_flow_id | bigint unsigned | 是 | NULL | 当前审批流ID |
| current_step_id | bigint unsigned | 是 | NULL | 当前审批步骤ID |
| submit_by | bigint unsigned | 是 | NULL | 提交人 |
| submit_time | datetime | 否 | CURRENT_TIMESTAMP | 提交时间 |
| approve_by | bigint unsigned | 是 | NULL | 审批人 |
| approve_time | datetime | 是 | NULL | 审批时间 |
| approve_opinion | varchar(500) | 是 | NULL | 审批意见 |
| initial_review_by | bigint unsigned | 是 | NULL | 初审人 |
| initial_review_time | datetime | 是 | NULL | 初审时间 |
| initial_review_opinion | varchar(500) | 是 | NULL | 初审意见 |
| final_review_by | bigint unsigned | 是 | NULL | 复审人 |
| final_review_time | datetime | 是 | NULL | 复审时间 |
| final_review_opinion | varchar(500) | 是 | NULL | 复审意见 |
| return_reason | varchar(500) | 是 | NULL | 退回原因 |
| reject_reason | varchar(500) | 是 | NULL | 驳回原因 |
| is_volume_based | tinyint | 否 | 0 | 是否带量 |
| is_centralized_procurement | tinyint | 否 | 0 | 是否集采 |
| is_domestic | tinyint | 否 | 1 | 是否国产 |
| contract_code | varchar(80) | 是 | NULL | 合同编码 |
| first_category | varchar(80) | 是 | NULL | 一级分类 |
| second_category | varchar(80) | 是 | NULL | 二级分类 |
| third_category | varchar(80) | 是 | NULL | 三级分类 |
| is_chargeable | tinyint | 否 | 1 | 是否收费 |
| tender_sub_code | varchar(80) | 是 | NULL | 招采子编码 |
| approval_round | int unsigned | 否 | 1 | 审批轮次 |

## pending_product_approval_action

待审批目录不可变审批动作

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| action_id | bigint unsigned | 否 | 无 | 目录审批动作主键；自增主键 |
| application_id | bigint unsigned | 否 | 无 | 目录申请ID，关联pending_product_application |
| approval_round | int unsigned | 否 | 无 | 审批轮次，退回重提递增并保留旧轮次 |
| flow_id | bigint unsigned | 是 | NULL | 审批流程ID，关联approval_flow |
| step_id | bigint unsigned | 是 | NULL | 来源审批步骤ID，关联approval_flow_step |
| step_order | int | 否 | 无 | 来源审批步骤顺序 |
| actor_id | bigint unsigned | 否 | 无 | 审批操作人用户ID，关联sys_user |
| action | varchar(20) | 否 | 无 | 审批操作类型：approve通过、return退回、reject拒绝 |
| opinion | varchar(500) | 是 | NULL | 审批意见 |
| from_status | varchar(30) | 否 | 无 | 审批操作前的申请状态 |
| to_status | varchar(30) | 否 | 无 | 审批操作后的申请状态 |
| source_type | varchar(20) | 否 | runtime | 审批动作来源类型，legacy_import表示历史数据补录 |
| action_time | datetime | 否 | CURRENT_TIMESTAMP | 审批操作发生时间 |

## pending_product_approval_route_step

目录申请不可变审批路由快照

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| route_step_id | bigint unsigned | 否 | 无 | 目录申请审批路由快照主键；自增主键 |
| application_id | bigint unsigned | 否 | 无 | 目录申请ID，关联pending_product_application |
| approval_round | int unsigned | 否 | 无 | 审批轮次，退回重提递增并保留旧轮次 |
| route_order | int unsigned | 否 | 无 | 本轮审批连续顺序，从1开始 |
| feature_code | varchar(80) | 否 | 无 | 业务模块编码 |
| node_code | varchar(80) | 否 | 无 | 审批业务节点编码 |
| flow_id | bigint unsigned | 否 | 无 | 审批流程ID，关联approval_flow |
| source_step_id | bigint unsigned | 否 | 无 | 提交时来源审批步骤ID，关联approval_flow_step |
| source_step_order | int unsigned | 否 | 无 | 提交时来源配置的原始步骤顺序 |
| step_name | varchar(100) | 否 | 无 | 提交时审批步骤名称 |
| approver_type | varchar(30) | 否 | 无 | 审批参与方式：role角色、user指定用户、dept_manager部门负责人 |
| role_id | bigint unsigned | 是 | NULL | 角色ID，关联sys_role |
| user_id | bigint unsigned | 是 | NULL | 用户ID，关联sys_user |
| dept_id | bigint unsigned | 是 | NULL | 科室ID |
| min_approvals | int unsigned | 否 | 1 | 该节点最少通过人数 |
| allow_self_approve | tinyint | 否 | 0 | 是否允许提交人审批自己的申请：0否、1是 |
| data_scope | tinyint | 否 | 1 | 数据权限范围：1全部、2本部门、3本部门及下级、4仅本人 |
| route_status | varchar(20) | 否 | waiting | 路由节点状态：waiting等待、pending待审批、completed已完成 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| complete_time | datetime | 是 | NULL | 审批节点完成时间 |

## print_template

打印模板表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| template_id | bigint unsigned | 否 | 无 | 模板ID；自增主键 |
| template_code | varchar(50) | 否 | 无 | 模板编码 |
| template_name | varchar(100) | 否 | 无 | 模板名称 |
| template_type | varchar(30) | 否 | 无 | 模板类型: quota_label 定数包标签 |
| fields_json | json | 是 | NULL | 字段配置 JSON |
| paper_width_mm | decimal(8,2) | 否 | 100.00 | 纸张宽度(mm) |
| paper_height_mm | decimal(8,2) | 否 | 70.00 | 纸张高度(mm) |
| status | tinyint | 否 | 1 | 状态: 1启用 0停用 |
| remark | varchar(500) | 是 | NULL | 备注 |
| create_by | bigint unsigned | 是 | NULL | 创建人 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## product

商品表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| product_id | bigint unsigned | 否 | 无 | 商品ID；自增主键 |
| product_code | varchar(50) | 否 | 无 | 商品编码 |
| product_name | varchar(100) | 否 | 无 | 商品名称 |
| generic_name | varchar(100) | 是 | NULL | 通用名称 |
| spec_model | varchar(100) | 否 | 无 | 规格型号 |
| brand | varchar(50) | 是 | NULL | 品牌 |
| manufacturer_id | bigint unsigned | 是 | NULL | 厂家ID |
| supplier_id | bigint unsigned | 是 | NULL | 默认供应商ID |
| category_id | bigint unsigned | 否 | 无 | 商品分类ID |
| unit | varchar(20) | 否 | 无 | 基本单位 |
| purchase_price | decimal(18,4) | 否 | 0.0000 | 采购价 |
| retail_price | decimal(18,4) | 是 | NULL | 零售价 |
| min_purchase_qty | decimal(18,4) | 否 | 1.0000 | 最小采购量 |
| purchase_unit | varchar(20) | 是 | NULL | 采购单位 |
| conversion_rate | decimal(18,6) | 否 | 1.000000 | 换算系数 |
| purchase_package_qty | decimal(18,3) | 是 | NULL | 采购包装数量 |
| udi_code | varchar(100) | 是 | NULL | UDI编码 |
| medical_insurance_code | varchar(80) | 是 | NULL | 国家医保耗材编码 |
| registration_no | varchar(100) | 是 | NULL | 注册证号 |
| registration_expire_date | date | 是 | NULL | 注册证有效期 |
| production_license_no | varchar(100) | 是 | NULL | 生产许可证号 |
| business_license_no | varchar(100) | 是 | NULL | 经营许可证号 |
| is_high_value | tinyint | 否 | 0 | 是否高值耗材 |
| is_cold_chain | tinyint | 否 | 0 | 是否冷链 |
| is_quota_managed | tinyint | 否 | 0 | 是否定数管理 |
| is_key_monitored | tinyint | 否 | 0 | 是否重点监控 |
| storage_condition | varchar(30) | 是 | NULL | 储存条件 |
| image_url | varchar(500) | 是 | NULL | 商品图片 |
| status | tinyint | 否 | 1 | 状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |
| is_volume_based | tinyint | 否 | 0 | 是否带量 |
| volume_based_type | varchar(50) | 是 | NULL | 带量类型 |
| is_centralized_procurement | tinyint | 否 | 0 | 是否集采 |
| is_domestic | tinyint | 否 | 1 | 是否国产 |
| contract_code | varchar(80) | 是 | NULL | 合同编码 |
| first_category | varchar(80) | 是 | NULL | 一级分类 |
| second_category | varchar(80) | 是 | NULL | 二级分类 |
| third_category | varchar(80) | 是 | NULL | 三级分类 |
| is_chargeable | tinyint | 否 | 1 | 是否收费 |
| is_medical_insurance_payment | tinyint | 否 | 0 | 是否医保支付 |
| medical_insurance_payment_type | varchar(50) | 是 | NULL | 医保支付类型 |
| tender_sub_code | varchar(80) | 是 | NULL | 招采子编码 |

## product_category

商品目录分类字段表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| category_id | bigint unsigned | 否 | 无 | 商品分类ID；自增主键 |
| parent_id | bigint unsigned | 否 | 0 | 上级分类ID |
| category_code | varchar(50) | 否 | 无 | 分类编码 |
| category_name | varchar(80) | 否 | 无 | 分类名称 |
| level | tinyint | 否 | 无 | 层级：1/2/3 |
| sort_order | int | 否 | 0 | 排序 |
| status | tinyint | 否 | 1 | 状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## product_guided_location

散货引导货位表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | 无 | 记录主键；自增主键 |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| primary_location_id | bigint unsigned | 否 | 无 | 引导货位 |
| backup_location_id | bigint unsigned | 是 | NULL | 备用货位 |
| min_stock | decimal(18,4) | 否 | 0.0000 | 最低存量 |
| max_stock | decimal(18,4) | 否 | 0.0000 | 最高存量 |
| deleted | tinyint | 否 | 0 | 删除标记 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |

## purchase_demand

采购需求记录

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| demand_id | bigint unsigned | 否 | 无 | 采购需求ID，关联purchase_demand；自增主键 |
| demand_no | varchar(50) | 否 | 无 | 采购需求单号 |
| demand_source | varchar(30) | 否 | 无 | 采购需求来源类型 |
| demand_status | varchar(30) | 否 | draft | 采购需求业务状态 |
| urgent_level | varchar(30) | 否 | normal | 采购需求紧急程度 |
| dept_id | bigint unsigned | 是 | NULL | 科室ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID，关联product |
| quantity | decimal(18,4) | 否 | 无 | 业务数量 |
| approved_quantity | decimal(18,4) | 否 | 0.0000 | 审核通过的需求数量 |
| suggested_purchase_qty | decimal(18,4) | 否 | 0.0000 | 建议采购数量 |
| remark | varchar(500) | 是 | NULL | 备注 |
| create_by | bigint unsigned | 是 | NULL | 创建人 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| approve_time | datetime | 是 | NULL | 审批时间 |

## purchase_order

采购订单表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| purchase_order_id | bigint unsigned | 否 | 无 | 采购订单ID；自增主键 |
| order_no | varchar(50) | 否 | 无 | 采购订单号 |
| supplier_id | bigint unsigned | 否 | 无 | 供应商ID |
| order_source | varchar(30) | 是 | NULL | 需求来源 |
| purchase_type | varchar(30) | 是 | NULL | 采购业务类型 |
| order_status | varchar(30) | 否 | draft | 订单状态 |
| total_amount | decimal(18,4) | 否 | 0.0000 | 订单总额 |
| expected_arrival_date | date | 是 | NULL | 期望到货日期 |
| create_by | bigint unsigned | 是 | NULL | 创建人 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| approve_by | bigint unsigned | 是 | NULL | 审核人 |
| approve_time | datetime | 是 | NULL | 审核时间 |
| send_time | datetime | 是 | NULL | 采购订单发送时间 |
| close_time | datetime | 是 | NULL | 采购订单关闭时间 |
| close_reason | varchar(500) | 是 | NULL | 采购订单关闭原因 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |

## purchase_order_item

采购订单明细表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| item_id | bigint unsigned | 否 | 无 | 明细ID；自增主键 |
| purchase_order_id | bigint unsigned | 否 | 无 | 采购订单ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| quantity | decimal(18,4) | 否 | 无 | 采购数量 |
| unit | varchar(20) | 否 | 无 | 单位 |
| estimated_unit_price | decimal(18,4) | 否 | 无 | 预估单价 |
| amount | decimal(18,4) | 否 | 无 | 金额 |
| received_quantity | decimal(18,4) | 否 | 0.0000 | 已收货数量 |

## purchase_order_tracking

采购订单业务跟踪事件

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| tracking_id | bigint unsigned | 否 | 无 | 采购订单跟踪记录主键；自增主键 |
| purchase_order_id | bigint unsigned | 否 | 无 | 采购订单ID，关联purchase_order |
| event_type | varchar(50) | 否 | 无 | 业务事件类型编码 |
| event_status | varchar(50) | 否 | 无 | 采购订单跟踪事件状态 |
| remark | varchar(500) | 是 | NULL | 备注 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## purchase_plan

采购计划

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| plan_id | bigint unsigned | 否 | 无 | 采购计划ID，关联purchase_plan；自增主键 |
| plan_no | varchar(50) | 否 | 无 | 采购计划单号 |
| plan_status | varchar(30) | 否 | draft | 采购计划业务状态 |
| supplier_id | bigint unsigned | 否 | 无 | 供应商ID，关联supplier |
| product_id | bigint unsigned | 否 | 无 | 商品ID，关联product |
| planned_quantity | decimal(18,4) | 否 | 无 | 采购计划数量 |
| converted_order_no | varchar(50) | 是 | NULL | 转换生成的采购订单号 |
| remark | varchar(500) | 是 | NULL | 备注 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| approve_time | datetime | 是 | NULL | 审批时间 |

## purchase_plan_demand

采购计划来源需求关系

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| plan_id | bigint unsigned | 否 | 无 | 采购计划ID，关联purchase_plan |
| demand_id | bigint unsigned | 否 | 无 | 采购需求ID，关联purchase_demand |
| allocated_quantity | decimal(18,4) | 否 | 无 | 需求分配到采购计划的数量 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## purchase_replenishment_analysis

采购智能补货分析主表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| analysis_id | bigint unsigned | 否 | 无 | 采购补货分析ID；自增主键 |
| analysis_no | varchar(50) | 否 | 无 | 采购补货分析编号 |
| selected_period_days | int | 否 | 无 | 选择周期天数 |
| item_count | int | 否 | 0 | 分析商品数 |
| total_formula_qty | decimal(18,4) | 否 | 0.0000 | 公式建议总量 |
| total_recommended_qty | decimal(18,4) | 否 | 0.0000 | 最终建议总量 |
| analysis_status | varchar(30) | 否 | analyzed | 分析状态 |
| remark | varchar(500) | 是 | NULL | 备注 |
| analysis_time | datetime | 否 | CURRENT_TIMESTAMP | 分析时间 |

## purchase_replenishment_analysis_item

采购智能补货分析明细表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| item_id | bigint unsigned | 否 | 无 | 采购补货分析明细ID；自增主键 |
| analysis_id | bigint unsigned | 否 | 无 | 采购补货分析ID |
| warehouse_code | varchar(50) | 否 | 无 | 一级库编码 |
| warehouse_name | varchar(80) | 否 | 无 | 一级库名称 |
| warehouse_type | varchar(30) | 是 | NULL | 库房类型 |
| supplier_name | varchar(120) | 是 | NULL | 供应商名称 |
| product_code | varchar(50) | 否 | 无 | 商品编码 |
| product_name | varchar(120) | 否 | 无 | 商品名称 |
| spec_model | varchar(120) | 是 | NULL | 规格型号 |
| unit | varchar(20) | 是 | NULL | 单位 |
| purchase_price | decimal(18,4) | 是 | NULL | 采购单价 |
| min_purchase_qty | decimal(18,4) | 否 | 0.0000 | 最小采购量 |
| current_qty | decimal(18,4) | 否 | 0.0000 | 一级库当前可用库存 |
| issue_5_qty | decimal(18,4) | 否 | 0.0000 | 5天出库数量 |
| issue_15_qty | decimal(18,4) | 否 | 0.0000 | 15天出库数量 |
| issue_30_qty | decimal(18,4) | 否 | 0.0000 | 30天出库数量 |
| issue_45_qty | decimal(18,4) | 否 | 0.0000 | 45天出库数量 |
| issue_60_qty | decimal(18,4) | 否 | 0.0000 | 60天出库数量 |
| selected_issue_qty | decimal(18,4) | 否 | 0.0000 | 所选周期出库数量 |
| formula_replenish_qty | decimal(18,4) | 否 | 0.0000 | 公式补货数量 |
| recommended_qty | decimal(18,4) | 否 | 0.0000 | 最终建议采购数量 |
| manual_adjusted_qty | decimal(18,4) | 是 | NULL | 人工调整数量 |
| formula_text | varchar(255) | 是 | NULL | 公式说明 |

## quota_package_event

定数包业务事件流水

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| event_id | bigint unsigned | 否 | 无 | 业务事件主键；自增主键 |
| event_no | varchar(50) | 否 | 无 | 业务事件编号 |
| label_id | bigint unsigned | 是 | NULL | 定数包标签ID，关联quota_package_label |
| event_type | varchar(50) | 否 | 无 | 定数包业务事件类型编码 |
| status_before | varchar(30) | 是 | NULL | 事件发生前的定数包标签状态 |
| status_after | varchar(30) | 是 | NULL | 事件发生后的定数包标签状态 |
| qty_change | decimal(18,4) | 否 | 0.0000 | 事件涉及的包内商品数量（商品最小单位），正负号按事件类型解释 |
| remark | varchar(500) | 是 | NULL | 备注 |
| event_time | datetime | 否 | CURRENT_TIMESTAMP | 业务事件发生时间 |

## quota_package_label

定数包标签与生命周期状态

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| label_id | bigint unsigned | 否 | 无 | 定数包标签ID，关联quota_package_label；自增主键 |
| label_no | varchar(50) | 否 | 无 | 定数包标签编号 |
| trace_code_id | bigint unsigned | 是 | NULL | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成 |
| task_id | bigint unsigned | 是 | NULL | 来源打包任务ID，关联quota_packing_task |
| template_id | bigint unsigned | 否 | 无 | 定数包模板ID，关联quota_package_template |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID，关联warehouse |
| product_id | bigint unsigned | 否 | 无 | 商品ID，关联product |
| package_quantity | decimal(18,4) | 否 | 无 | 每包所含商品数量（商品最小单位） |
| status | varchar(30) | 否 | available | 标签状态：pending_print待打印、available可用、picked已拣配、signed已签收、consumed已消耗、settled已结算、void已作废 |
| version | int | 否 | 1 | 并发控制版本号 |
| print_count | int | 否 | 0 | 标签打印次数 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 记录最后更新时间；更新时自动刷新 |

## quota_package_label_source

定数包标签来源库存批次及数量分摊

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| source_id | bigint unsigned | 否 | 无 | 定数包标签来源明细主键；自增主键 |
| label_id | bigint unsigned | 否 | 无 | 定数包标签ID，关联quota_package_label |
| batch_id | bigint unsigned | 否 | 无 | 库存批次ID，关联inventory_batch |
| source_qty | decimal(18,4) | 否 | 无 | 该来源批次分摊到本包的数量（商品最小单位） |
| unit_price | decimal(18,4) | 否 | 无 | 采购单价 |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID，关联warehouse |

## quota_package_template

定数包模板表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| template_id | bigint unsigned | 否 | 无 | 定数包模板ID；自增主键 |
| template_code | varchar(80) | 否 | 无 | 模板编码 |
| template_name | varchar(100) | 否 | 无 | 模板名称 |
| version_no | int | 否 | 1 | 模板版本 |
| is_current | tinyint | 否 | 1 | 是否当前版本 |
| superseded_by_id | bigint unsigned | 是 | NULL | 后继模板版本ID |
| effective_from | datetime | 否 | CURRENT_TIMESTAMP | 生效时间 |
| effective_to | datetime | 是 | NULL | 失效时间 |
| dept_id | bigint unsigned | 是 | NULL | 适用科室 |
| status | tinyint | 否 | 1 | 状态 |
| deleted | tinyint | 否 | 0 | 删除标记 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |

## quota_package_template_item

定数包模板明细表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| item_id | bigint unsigned | 否 | 无 | 模板明细ID；自增主键 |
| template_id | bigint unsigned | 否 | 无 | 模板ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| quantity | decimal(18,4) | 否 | 无 | 数量 |
| deleted | tinyint | 否 | 0 | 删除标记 |
| unit | varchar(20) | 否 | 无 | 单位 |

## quota_packing_task

定数包打包任务

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| task_id | bigint unsigned | 否 | 无 | 业务任务主键或关联任务ID；自增主键 |
| task_no | varchar(50) | 否 | 无 | 业务任务编号 |
| template_id | bigint unsigned | 否 | 无 | 定数包模板ID，关联quota_package_template |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID，关联warehouse |
| product_id | bigint unsigned | 否 | 无 | 商品ID，关联product |
| package_count | decimal(18,4) | 否 | 无 | 计划打包数量（包） |
| package_quantity | decimal(18,4) | 否 | 无 | 每包商品数量（商品最小单位），提交时模板快照 |
| planned_loose_qty | decimal(18,4) | 否 | 无 | 计划使用散货数量（商品最小单位） |
| reserved_loose_qty | decimal(18,4) | 否 | 0.0000 | 已预占散货数量（商品最小单位） |
| status | varchar(30) | 否 | pending_confirm | 打包状态：pending_confirm待确认、need_recalculate待重算、confirmed已确认、cancelled已取消、terminated已终止 |
| remark | varchar(500) | 是 | NULL | 备注 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| confirm_time | datetime | 是 | NULL | 打包确认时间 |
| cancel_time | datetime | 是 | NULL | 取消或终止时间 |

## quota_packing_task_reservation

定数包打包任务库存预占明细

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| reservation_id | bigint unsigned | 否 | 无 | 打包任务库存预占明细主键；自增主键 |
| task_id | bigint unsigned | 否 | 无 | 打包任务ID，关联quota_packing_task |
| balance_id | bigint unsigned | 否 | 无 | 库存余额ID，关联inventory_balance |
| batch_id | bigint unsigned | 否 | 无 | 库存批次ID，关联inventory_batch |
| receiving_no | varchar(50) | 是 | NULL | 来源验收单号 |
| receiving_item_id | bigint unsigned | 是 | NULL | 来源验收明细ID |
| reserved_qty | decimal(18,4) | 否 | 无 | 该批次预占散货数量（商品最小单位） |
| unit_price | decimal(18,4) | 否 | 无 | 采购单价 |
| status | varchar(30) | 否 | reserved | 预占状态：reserved已预占、consumed已转为打包消耗、released已释放、terminated已终止 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 记录最后更新时间；更新时自动刷新 |

## quota_safety_stock

定数安全量表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| safety_id | bigint unsigned | 否 | 无 | 安全量ID；自增主键 |
| dept_id | bigint unsigned | 否 | 无 | 科室ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| template_id | bigint unsigned | 是 | NULL | 定数包模板ID |
| min_qty | decimal(18,4) | 否 | 0.0000 | 安全下限 |
| max_qty | decimal(18,4) | 否 | 0.0000 | 安全上限 |
| status | tinyint | 否 | 1 | 状态 |
| deleted | tinyint | 否 | 0 | 删除标记 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |

## recall_event

商品批次召回或隔离记录

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| recall_id | bigint unsigned | 否 | 无 | 召回或隔离记录主键；自增主键 |
| recall_no | varchar(50) | 否 | 无 | 召回或隔离单号 |
| warehouse_name | varchar(80) | 否 | 无 | 库房名称 |
| product_code | varchar(50) | 否 | 无 | 商品编码 |
| product_name | varchar(120) | 否 | 无 | 商品名称 |
| batch_id | bigint unsigned | 是 | NULL | 召回批次ID |
| affected_qty | decimal(18,4) | 否 | 无 | 受影响商品数量（商品最小单位） |
| status | varchar(30) | 否 | 无 | 召回或隔离执行状态，isolated表示库存已隔离 |
| reason | varchar(500) | 是 | NULL | 业务原因 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| business_type | varchar(20) | 否 | recall | recall 召回，isolate 原地隔离 |

## receiving_order

收货验收单表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| receiving_order_id | bigint unsigned | 否 | 无 | 收货单ID；自增主键 |
| receiving_no | varchar(50) | 否 | 无 | 收货单号 |
| purchase_order_id | bigint unsigned | 是 | NULL | 采购订单ID |
| supplier_id | bigint unsigned | 是 | NULL | 供应商ID，期初库存可为空 |
| warehouse_id | bigint unsigned | 否 | 无 | 入库库房 |
| receiving_type | varchar(30) | 是 | NULL | 收货类型 |
| is_agent | tinyint | 否 | 0 | 是否代理商 |
| opening_source | varchar(20) | 是 | NULL | 期初数据来源：manual/import |
| receiving_status | varchar(30) | 否 | draft | 收货状态 |
| receive_time | datetime | 是 | NULL | 收货时间 |
| receiver_id | bigint unsigned | 是 | NULL | 收货人 |
| remark | varchar(500) | 是 | NULL | 备注 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |

## receiving_order_item

收货验收明细表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| item_id | bigint unsigned | 否 | 无 | 明细ID；自增主键 |
| receiving_order_id | bigint unsigned | 否 | 无 | 收货单ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| production_batch_no | varchar(50) | 是 | NULL | 生产批号 |
| udi_code | varchar(120) | 是 | NULL | 验收录入UDI（独立于唯一码） |
| production_date | date | 是 | NULL | 生产日期 |
| expire_date | date | 是 | NULL | 有效期至 |
| quantity | decimal(18,4) | 否 | 无 | 收货数量 |
| unit_price | decimal(18,4) | 否 | 无 | 验收批次单价 |
| amount | decimal(18,4) | 否 | 无 | 金额 |
| qualified_quantity | decimal(18,4) | 否 | 0.0000 | 合格数量 |
| unqualified_quantity | decimal(18,4) | 否 | 0.0000 | 不合格数量 |

## replenishment_analysis_requisition

智能补货分析生成申领关联

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| analysis_id | bigint unsigned | 否 | 无 | 科室智能补货分析ID，关联replenishment_smart_analysis |
| requisition_id | bigint unsigned | 否 | 无 | 科室申领单ID，关联department_requisition |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## replenishment_smart_analysis

智能补货分析主表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| analysis_id | bigint unsigned | 否 | 无 | 分析ID；自增主键 |
| dept_id | bigint unsigned | 是 | NULL | 稳定科室ID |
| warehouse_id | bigint unsigned | 是 | NULL | 目标库房ID |
| source_warehouse_id | bigint unsigned | 是 | NULL | 来源中心库ID |
| created_by | bigint unsigned | 是 | NULL | 分析人 |
| status | varchar(30) | 否 | draft | 分析状态：draft草稿、generated已生成申领 |
| dept_name | varchar(80) | 否 | 无 | 科室名称 |
| warehouse_name | varchar(80) | 否 | 无 | 库房名称 |
| selected_period_days | int | 否 | 无 | 选择周期天数 |
| item_count | int | 否 | 0 | 分析商品数 |
| total_recommended_qty | decimal(18,4) | 否 | 0.0000 | 建议补货总量 |
| analysis_time | datetime | 否 | CURRENT_TIMESTAMP | 分析时间 |

## replenishment_smart_analysis_item

智能补货分析明细表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| item_id | bigint unsigned | 否 | 无 | 分析明细ID；自增主键 |
| analysis_id | bigint unsigned | 否 | 无 | 分析ID |
| product_id | bigint unsigned | 是 | NULL | 稳定商品ID |
| item_mode | varchar(30) | 否 | loose | 申领模式 |
| quota_template_id | bigint unsigned | 是 | NULL | 定数包模板版本ID |
| quota_template_version | int | 是 | NULL | 定数包模板版本 |
| package_quantity | decimal(18,4) | 是 | NULL | 包内基础数量 |
| dept_id | bigint unsigned | 是 | NULL | 稳定科室ID |
| warehouse_id | bigint unsigned | 是 | NULL | 目标库房ID |
| source_warehouse_id | bigint unsigned | 是 | NULL | 来源中心库ID |
| product_code | varchar(50) | 否 | 无 | 商品编码 |
| product_name | varchar(120) | 否 | 无 | 商品名称 |
| issue_5_qty | decimal(18,4) | 否 | 0.0000 | 5天出库数量 |
| issue_7_qty | decimal(18,4) | 否 | 0.0000 | 7天出库数量 |
| issue_15_qty | decimal(18,4) | 否 | 0.0000 | 15天出库数量 |
| issue_30_qty | decimal(18,4) | 否 | 0.0000 | 30天出库数量 |
| current_qty | decimal(18,4) | 否 | 0.0000 | 当前库存 |
| source_available_qty | decimal(18,4) | 否 | 0.0000 | 分析时来源库可用量 |
| recommended_qty | decimal(18,4) | 否 | 0.0000 | 建议补货数量 |
| manual_adjusted_qty | decimal(18,4) | 是 | NULL | 人工调整申领量 |
| selected | tinyint | 否 | 1 | 是否选中生成 |
| formula_text | varchar(255) | 是 | NULL | 公式说明 |

## settlement_bill

结算单表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| settlement_id | bigint unsigned | 否 | 无 | 结算单ID；自增主键 |
| settlement_no | varchar(50) | 否 | 无 | 结算单号 |
| supplier_id | bigint unsigned | 否 | 无 | 供应商ID |
| settlement_period | varchar(20) | 否 | 无 | 结算期间 |
| settlement_mode | varchar(30) | 否 | 无 | 结算模式 |
| total_amount | decimal(18,4) | 否 | 0.0000 | 结算金额 |
| status | varchar(30) | 否 | draft | 状态 |
| generate_time | datetime | 否 | CURRENT_TIMESTAMP | 生成时间 |
| confirm_time | datetime | 是 | NULL | 确认时间 |

## settlement_bill_item

结算单明细表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| item_id | bigint unsigned | 否 | 无 | 明细ID；自增主键 |
| settlement_id | bigint unsigned | 否 | 无 | 结算单ID |
| source_biz_type | varchar(50) | 否 | 无 | 来源业务类型 |
| source_biz_id | bigint unsigned | 否 | 无 | 来源业务ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| batch_id | bigint unsigned | 是 | NULL | 系统批次ID |
| trace_code_id | bigint unsigned | 是 | NULL | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成 |
| quantity | decimal(18,4) | 否 | 无 | 数量 |
| unit_price | decimal(18,4) | 否 | 无 | 单价 |
| amount | decimal(18,4) | 否 | 无 | 金额 |

## shortage_replenishment_task

科室短缺补货任务

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| task_id | bigint unsigned | 否 | 无 | 业务任务主键或关联任务ID；自增主键 |
| task_no | varchar(50) | 否 | 无 | 业务任务编号 |
| dept_name | varchar(80) | 否 | 无 | 科室名称 |
| product_code | varchar(50) | 否 | 无 | 商品编码 |
| product_name | varchar(120) | 否 | 无 | 商品名称 |
| min_qty | decimal(18,4) | 否 | 无 | 库存下限数量（商品最小单位） |
| current_qty | decimal(18,4) | 否 | 无 | 当前库存数量（商品最小单位） |
| replenish_qty | decimal(18,4) | 否 | 无 | 建议补货数量（商品最小单位） |
| period_days | int | 否 | 7 | 补货周期天数 |
| period_issue_qty | decimal(18,4) | 否 | 0.0000 | 周期出库数量 |
| avg_daily_issue_qty | decimal(18,4) | 否 | 0.0000 | 日均出库数量 |
| formula_replenish_qty | decimal(18,4) | 否 | 0.0000 | 公式建议补货数量 |
| manual_adjusted | tinyint(1) | 否 | 0 | 是否人工调整 |
| formula_text | varchar(255) | 是 | NULL | 补货公式说明 |
| status | varchar(30) | 否 | 无 | 短缺补货任务状态，pending_replenish表示待补货 |
| source_type | varchar(50) | 是 | NULL | 业务来源类型 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## spd_delivery_batch

配送单来源库存批次与扣减事件关联

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| delivery_id | bigint unsigned | 否 | 无 | 配送单ID，关联spd_delivery_order |
| source_event_id | bigint unsigned | 否 | 无 | 来源库存扣减事件ID，关联inventory_event |
| source_warehouse_id | bigint unsigned | 否 | 无 | 来源库房ID，关联warehouse |
| product_id | bigint unsigned | 否 | 无 | 商品ID，关联product |
| batch_id | bigint unsigned | 否 | 无 | 库存批次ID，关联inventory_batch |
| quantity | decimal(18,4) | 否 | 无 | 该配送单从来源批次扣减的数量（商品最小单位） |

## spd_delivery_order

SPD科室拣配配送单

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| delivery_id | bigint unsigned | 否 | 无 | 配送单ID，关联spd_delivery_order；自增主键 |
| delivery_no | varchar(50) | 否 | 无 | 配送单号 |
| requisition_no | varchar(50) | 是 | NULL | 来源科室申领单号 |
| requisition_item_id | bigint unsigned | 是 | NULL | 关联申领明细ID |
| delivery_type | varchar(20) | 是 | NULL | 拣配类型: package/unique_code/loose |
| dept_name | varchar(80) | 否 | 无 | 科室名称 |
| warehouse_name | varchar(80) | 否 | 无 | 库房名称 |
| destination_warehouse_id | bigint unsigned | 是 | NULL | 科室目标库房 |
| product_code | varchar(50) | 否 | 无 | 商品编码 |
| product_name | varchar(120) | 否 | 无 | 商品名称 |
| quantity | decimal(18,4) | 否 | 无 | 业务数量 |
| allocation_mode | varchar(30) | 否 | loose | 分配方式：loose=散货，quota_package=定数包 |
| status | varchar(30) | 否 | 无 | 拣配配送单业务状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| sign_time | datetime | 是 | NULL | 配送签收时间 |

## spd_delivery_package_binding

拣配配送定数包绑定表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| binding_id | bigint unsigned | 否 | 无 | 拣配绑定ID；自增主键 |
| delivery_id | bigint unsigned | 否 | 无 | 配送单ID |
| requisition_id | bigint unsigned | 否 | 无 | 科室申领单ID |
| requisition_item_id | bigint unsigned | 否 | 无 | 科室申领明细ID |
| label_id | bigint unsigned | 否 | 无 | 定数包标签ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| package_quantity | decimal(18,4) | 否 | 无 | 绑定数量 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |

## spd_delivery_quota_package

配送定数包分配表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| allocation_id | bigint unsigned | 否 | 无 | 配送定数包分配ID；自增主键 |
| delivery_id | bigint unsigned | 否 | 无 | 配送单ID |
| label_id | bigint unsigned | 否 | 无 | 定数包标签ID |
| label_no | varchar(50) | 否 | 无 | 定数包标签号 |
| package_quantity | decimal(18,4) | 否 | 无 | 包内数量 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |

## spd_delivery_trace_code

配送单与收费耗材追溯单元关联

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| delivery_id | bigint unsigned | 否 | 无 | 配送单ID，关联spd_delivery_order |
| trace_code_id | bigint unsigned | 否 | 无 | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## supplier

供应商表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| supplier_id | bigint unsigned | 否 | 无 | 供应商ID；自增主键 |
| supplier_code | varchar(50) | 否 | 无 | 供应商编码 |
| supplier_name | varchar(100) | 否 | 无 | 供应商名称 |
| credit_code | varchar(18) | 否 | 无 | 统一社会信用代码 |
| business_license_no | varchar(80) | 是 | NULL | 经营许可证号 |
| supplier_type | varchar(20) | 否 | 无 | 供应商类型 |
| grade | char(1) | 是 | NULL | 供应商等级：A/B/C/D |
| contact_name | varchar(50) | 否 | 无 | 联系人 |
| contact_phone | varchar(30) | 否 | 无 | 联系电话 |
| email | varchar(100) | 是 | NULL | 邮箱 |
| address | varchar(200) | 是 | NULL | 地址 |
| approval_status | varchar(20) | 否 | draft | 审批状态 |
| status | tinyint | 否 | 1 | 状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## sys_attachment

附件表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| attachment_id | bigint unsigned | 否 | 无 | 附件ID；自增主键 |
| source_attachment_id | bigint unsigned | 是 | NULL | 审批归档来源附件ID |
| biz_type | varchar(50) | 否 | 无 | 业务类型 |
| biz_id | bigint unsigned | 否 | 无 | 业务ID |
| file_name | varchar(255) | 否 | 无 | 原始文件名 |
| file_ext | varchar(20) | 否 | 无 | 文件扩展名 |
| file_type | varchar(20) | 否 | 无 | 文件类型 |
| file_size | bigint unsigned | 否 | 无 | 文件大小 |
| file_path | varchar(500) | 否 | 无 | 文件路径 |
| file_url | varchar(500) | 否 | 无 | 访问URL |
| category | varchar(50) | 是 | other | 附件分类 |
| description | varchar(500) | 是 | NULL | 附件描述 |
| valid_date | date | 是 | NULL | 有效期 |
| create_by | bigint unsigned | 是 | NULL | 上传人 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 上传时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## sys_dept

科室表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| dept_id | bigint unsigned | 否 | 无 | 科室ID；自增主键 |
| parent_id | bigint unsigned | 否 | 0 | 上级科室ID |
| dept_code | varchar(50) | 否 | 无 | 科室编码 |
| dept_name | varchar(50) | 否 | 无 | 科室名称 |
| finance_dept_code | varchar(50) | 是 | NULL | 财务科室编码 |
| finance_dept_name | varchar(50) | 是 | NULL | 财务科室 |
| campus_name | varchar(50) | 是 | NULL | 院区 |
| address | varchar(200) | 是 | NULL | 科室地址 |
| manager_name | varchar(50) | 是 | NULL | 负责人 |
| phone | varchar(20) | 是 | NULL | 联系电话 |
| sort_order | int | 否 | 0 | 排序 |
| status | tinyint | 否 | 1 | 状态：0-禁用 1-启用 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## sys_field_option

表格下拉选字段选项字典

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| option_id | bigint unsigned | 否 | 无 | 字段选项主键；自增主键 |
| field_key | varchar(64) | 否 | 无 | 字段键 |
| field_label | varchar(100) | 否 | 无 | 字段名称 |
| option_value | varchar(100) | 否 | 无 | 选项值 |
| option_label | varchar(100) | 否 | 无 | 选项显示名 |
| sort_order | int | 否 | 0 | 显示排序号 |
| status | tinyint | 否 | 1 | 1启用 0停用 |
| deleted | tinyint | 否 | 0 | 软删除标记：0未删除、1已删除 |
| remark | varchar(255) | 是 | NULL | 备注 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 记录最后更新时间；更新时自动刷新 |

## sys_login_log

登录日志表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| log_id | bigint unsigned | 否 | 无 | 登录日志ID；自增主键 |
| user_id | bigint unsigned | 是 | NULL | 用户ID |
| username | varchar(50) | 是 | NULL | 用户名 |
| login_ip | varchar(50) | 是 | NULL | 登录IP |
| user_agent | varchar(500) | 是 | NULL | 客户端 |
| status | tinyint | 否 | 无 | 状态：0-失败 1-成功 |
| message | varchar(200) | 是 | NULL | 消息 |
| login_time | datetime | 否 | CURRENT_TIMESTAMP | 登录时间 |

## sys_permission

权限表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| perm_id | bigint unsigned | 否 | 无 | 权限ID；自增主键 |
| parent_id | bigint unsigned | 否 | 0 | 父权限ID |
| perm_name | varchar(80) | 否 | 无 | 权限名称 |
| perm_code | varchar(100) | 否 | 无 | 权限标识 |
| perm_type | tinyint | 否 | 1 | 权限类型：1-菜单 2-按钮 3-接口 |
| path | varchar(200) | 是 | NULL | 路由路径 |
| component | varchar(200) | 是 | NULL | 组件路径 |
| icon | varchar(50) | 是 | NULL | 菜单图标 |
| sort_order | int | 否 | 0 | 排序 |
| status | tinyint | 否 | 1 | 状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## sys_role

角色表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| role_id | bigint unsigned | 否 | 无 | 角色ID；自增主键 |
| role_name | varchar(50) | 否 | 无 | 角色名称 |
| role_code | varchar(50) | 否 | 无 | 角色编码 |
| description | varchar(200) | 是 | NULL | 角色描述 |
| data_scope | tinyint | 否 | 1 | 数据范围：1-全部 2-本部门 3-本部门及子部门 4-仅本人 |
| status | tinyint | 否 | 1 | 状态：0-禁用 1-启用 |
| sort_order | int | 否 | 0 | 排序 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## sys_role_dept

角色自定义数据权限部门

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| role_id | bigint unsigned | 否 | 无 | 角色ID，关联sys_role |
| dept_id | bigint unsigned | 否 | 无 | 科室ID |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |

## sys_role_perm

角色权限关系表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | 无 | 记录主键；自增主键 |
| role_id | bigint unsigned | 否 | 无 | 角色ID |
| perm_id | bigint unsigned | 否 | 无 | 权限ID |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |

## sys_sequence

业务单号持久化序列

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| seq_key | varchar(100) | 否 | 无 | 业务单号序列键，区分业务及编号周期 |
| seq_value | bigint unsigned | 否 | 0 | 该序列当前已分配值 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 记录最后更新时间；更新时自动刷新 |

## sys_user

用户表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| user_id | bigint unsigned | 否 | 无 | 用户ID；自增主键 |
| username | varchar(50) | 否 | 无 | 用户名 |
| password | varchar(100) | 否 | 无 | 密码 |
| real_name | varchar(50) | 是 | NULL | 真实姓名 |
| phone | varchar(20) | 是 | NULL | 手机号 |
| email | varchar(100) | 是 | NULL | 邮箱 |
| avatar | varchar(255) | 是 | NULL | 头像URL |
| gender | tinyint | 否 | 0 | 性别：0-未知 1-男 2-女 |
| dept_id | bigint unsigned | 是 | NULL | 所属科室ID |
| status | tinyint | 否 | 1 | 状态：0-禁用 1-启用 |
| login_fail_count | int | 否 | 0 | 连续登录失败次数 |
| lock_time | datetime | 是 | NULL | 锁定时间 |
| last_login_time | datetime | 是 | NULL | 最后登录时间 |
| last_login_ip | varchar(50) | 是 | NULL | 最后登录IP |
| create_by | bigint unsigned | 是 | NULL | 创建人ID |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_by | bigint unsigned | 是 | NULL | 更新人ID |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## sys_user_role

用户角色关系表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | 无 | 记录主键；自增主键 |
| user_id | bigint unsigned | 否 | 无 | 用户ID |
| role_id | bigint unsigned | 否 | 无 | 角色ID |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |

## sys_validation_rule

业务校验规则字段组合存储

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| rule_id | bigint unsigned | 否 | 无 | 业务校验规则主键；自增主键 |
| rule_code | varchar(64) | 否 | 无 | 规则编码 |
| rule_name | varchar(100) | 否 | 无 | 规则名称 |
| biz_scope | varchar(64) | 否 | 无 | 业务范围 |
| rule_fields | varchar(500) | 否 | 无 | 逗号分隔的字段键 |
| status | tinyint | 否 | 1 | 启用状态：0停用、1启用 |
| remark | varchar(255) | 是 | NULL | 备注 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 记录最后更新时间；更新时自动刷新 |

## system_config

系统配置表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| config_id | bigint unsigned | 否 | 无 | 配置ID；自增主键 |
| config_type | varchar(50) | 否 | 无 | 配置类型 |
| scope_type | varchar(30) | 否 | 无 | 适用范围 |
| scope_id | varchar(64) | 否 | 无 | 适用对象 |
| config_key | varchar(100) | 否 | 无 | 配置键 |
| config_value | json | 否 | 无 | 配置值 |
| effective_mode | varchar(20) | 否 | realtime | 生效方式 |
| effective_time | datetime | 否 | CURRENT_TIMESTAMP | 生效时间 |
| expire_time | datetime | 是 | NULL | 失效时间 |
| risk_level | varchar(20) | 是 | NULL | 风险等级 |
| status | tinyint | 否 | 1 | 状态 |
| deleted | tinyint | 否 | 0 | 删除标记 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |

## udi_trace_code

收费耗材与低值定数包统一追溯主档

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| trace_code_id | bigint unsigned | 否 | 无 | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成；自增主键 |
| udi_code | varchar(120) | 是 | NULL | UDI code（取验收录入UDI，独立于唯一码） |
| unique_code | varchar(120) | 否 | 无 | 业务唯一码文本 |
| trace_scope | varchar(40) | 否 | high_value | 追溯范围：high_value收费耗材、low_value_quota_pack低值定数包 |
| package_label_no | varchar(80) | 是 | NULL | 定数包标签编号 |
| template_code | varchar(80) | 是 | NULL | 定数包模板编码 |
| template_name | varchar(120) | 是 | NULL | 定数包模板名称 |
| package_quantity | decimal(18,4) | 是 | NULL | 每包所含商品数量（商品最小单位） |
| package_unit | varchar(30) | 是 | NULL | 定数包内商品计量单位 |
| package_status | varchar(40) | 是 | NULL | 定数包标签业务状态 |
| product_code | varchar(64) | 否 | 无 | 商品编码 |
| product_name | varchar(255) | 否 | 无 | 商品名称 |
| spec_model | varchar(255) | 是 | NULL | 规格型号 |
| manufacturer_name | varchar(255) | 是 | NULL | 厂家名称 |
| supplier_name | varchar(255) | 是 | NULL | 供应商名称 |
| batch_no | varchar(100) | 是 | NULL | 批号展示文本 |
| expire_date | date | 是 | NULL | 有效期截止日期 |
| current_location | varchar(255) | 是 | NULL | 当前所在位置 |
| current_department | varchar(255) | 是 | NULL | 当前所属科室 |
| current_status | varchar(40) | 否 | in_stock | 当前追溯业务状态 |
| responsible_person | varchar(80) | 是 | NULL | 当前责任人 |
| patient_no | varchar(80) | 是 | NULL | 患者编号 |
| patient_name_masked | varchar(80) | 是 | NULL | 脱敏患者姓名 |
| risk_level | varchar(40) | 否 | normal | 追溯风险等级 |
| last_event_name | varchar(100) | 是 | NULL | 最近追溯事件名称 |
| last_event_time | datetime | 是 | NULL | 最近追溯事件时间 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 记录创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 记录最后更新时间；更新时自动刷新 |

## udi_trace_event

UDI与唯一码追溯事件

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| trace_event_id | bigint unsigned | 否 | 无 | 追溯事件主键；自增主键 |
| trace_code_id | bigint unsigned | 否 | 无 | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成 |
| event_no | varchar(80) | 否 | 无 | 业务事件编号 |
| event_type | varchar(64) | 否 | 无 | 业务事件类型编码 |
| event_name | varchar(100) | 否 | 无 | 追溯事件名称 |
| biz_no | varchar(100) | 是 | NULL | 关联业务单号 |
| location_name | varchar(255) | 是 | NULL | 所在位置名称 |
| department_name | varchar(255) | 是 | NULL | 科室名称 |
| operator_name | varchar(80) | 是 | NULL | 操作人姓名或账号 |
| event_time | datetime | 否 | 无 | 业务事件发生时间 |
| status | varchar(40) | 否 | done | 追溯事件业务状态 |
| remark | varchar(255) | 是 | NULL | 备注 |
| sort_order | int | 否 | 0 | 显示排序号 |

## udi_trace_patient_binding

收费耗材追溯单元与患者绑定记录

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| binding_id | bigint unsigned | 否 | 无 | 患者绑定记录主键；自增主键 |
| trace_code_id | bigint unsigned | 否 | 无 | 稳定追溯主键，关联udi_trace_code；UDI或唯一码文本仅作展示和集成 |
| patient_no | varchar(80) | 否 | 无 | 患者编号 |
| patient_name_masked | varchar(80) | 是 | NULL | 脱敏患者姓名 |
| department_name | varchar(255) | 是 | NULL | 科室名称 |
| location_name | varchar(255) | 是 | NULL | 所在位置名称 |
| binding_status | varchar(30) | 否 | bound | 追溯单元患者绑定状态 |
| bind_time | datetime | 否 | CURRENT_TIMESTAMP | 患者绑定时间 |

## warehouse

库房表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID；自增主键 |
| warehouse_code | varchar(50) | 否 | 无 | 库房编码 |
| warehouse_name | varchar(50) | 否 | 无 | 库房名称 |
| warehouse_type | varchar(30) | 否 | 无 | 库房类型 |
| parent_id | bigint unsigned | 否 | 0 | 上级库房 |
| campus_name | varchar(50) | 否 | 无 | 所属院区 |
| dept_id | bigint unsigned | 是 | NULL | 关联科室 |
| manager_user_id | bigint unsigned | 是 | NULL | 库房负责人 |
| participate_stats | tinyint | 否 | 1 | 是否参与统计 |
| stats_categories | json | 是 | NULL | 统计要求分类 |
| status | tinyint | 否 | 1 | 状态 |
| receiving_enabled | tinyint | 否 | 0 | 是否允许收货验收入库 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## warehouse_location

货位表

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| location_id | bigint unsigned | 否 | 无 | 货位ID；自增主键 |
| warehouse_id | bigint unsigned | 否 | 无 | 所属库房 |
| location_code | varchar(80) | 否 | 无 | 货位编码 |
| location_type | varchar(30) | 否 | 无 | 货位类型 |
| capacity_limit | decimal(18,4) | 是 | NULL | 容量上限 |
| product_id | bigint unsigned | 是 | NULL | 固定关联商品 |
| status | tinyint | 否 | 1 | 状态 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |

## warehouse_product_binding

库房可管理商品绑定

| 字段 | 类型 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| binding_id | bigint unsigned | 否 | 无 | 库房商品绑定ID；自增主键 |
| warehouse_id | bigint unsigned | 否 | 无 | 库房ID |
| product_id | bigint unsigned | 否 | 无 | 商品ID |
| status | tinyint | 否 | 1 | 状态：0-停用 1-启用 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP | 更新时间；更新时自动刷新 |
| deleted | tinyint | 否 | 0 | 删除标记 |
