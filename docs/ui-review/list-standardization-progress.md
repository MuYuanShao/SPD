# 列表标准化与列宽持久化 — 本轮改造记录

## 参考标准页

科室库房目录 `frontend/src/views/master-data/MasterDataListView.vue`

## 本轮范围

| 板块 | 菜单 | 视图文件 |
|---|---|---|
| 专项与合规 | UDI / 唯一码追溯 | `supply-chain/UdiTraceabilityView.vue` |
| | 收费耗材明细查询 | `supply-chain/HighValueChargeDetailView.vue` |
| | 冷链监控 | `supply-chain/ColdChainMonitoringView.vue` |
| | 召回与隔离 | `supply-chain/OperationalClosureView.vue` |
| | PDA 离线记录 | `supply-chain/OperationalClosureView.vue` |
| | 系统操作记录 | `system/AuditLogView.vue` |
| 结算与财务 | 结算对账 | `supply-chain/OperationalClosureView.vue` |
| | 红冲管理 | `supply-chain/OperationalClosureView.vue` |
| | 价格调整 | `supply-chain/InventoryWorkbenchView.vue` |
| | 发票管理 | `supply-chain/InvoiceManagementView.vue` |
| 运营与决策 | 运营驾驶舱 | `dashboard/OperationCockpitView.vue` |
| | SPD-HIS收费核对 | `reports/SpdHisReconciliationReportView.vue` |
| | 供应商供货明细台账 | `reports/RegulatoryReportView.vue` |
| | 集采执行进度报表 | `reports/RegulatoryReportView.vue` |
| | 全院物资进销存汇总表 | `reports/RegulatoryReportView.vue` |
| | 进销存商品明细 | `reports/RegulatoryReportView.vue` |
| 系统管理 | 用户管理 | `system/UserRoleManagementView.vue` |
| | 角色权限 | `system/UserRoleManagementView.vue` |
| | 审批流设置 | `system/ApprovalFlowSettingsView.vue` |
| | 系统配置 | `system/SystemConfigView.vue` |
| | 字段管理 | `system/FieldOptionManagementView.vue` |
| | 配置命中说明 | `system/ConfigHitExplanationView.vue` |

## 列表标准要素

1. `el-table.ulc-table` + `MdDataTableCard` + `.ulc-table-wrap`
2. `useListColumns`（`fitMode: 'hybrid'`，列权重 / `minWidth` / `maxWidth` / `idealWidth`，每表独立 `storageKey`）
3. 长文本换行：`ulc-wrap-text` / `ulc-cell-stack` / `ulc-joined`；时间 `ulc-time`；状态 `ulc-chip`
4. 「列设置」按钮放在 `MdDataTableCard` 的 `#toolbar` 插槽内（`md-btn md-btn-primary` + `ulc-col-settings`）
5. 操作列仅在出现横向滚动条时右侧固定（`useActionColumnFix`）
6. 按钮统一 `md-btn` / `md-btn-primary` / `md-btn-danger`
7. 无顶部统计卡、无「共 N 条」角标
8. 筛选区用 `md-hospital-filter ulc-filter`（**不得**使用旧的 `hospital-query-grid`）

## 列宽持久化缺陷（已定位）

`frontend/src/composables/useListColumns.ts`

`handleHeaderDragEnd` 会把拖拽宽度写入 localStorage，但 `effectiveWidths` 计算属性在每次重算时
按权重把用户宽度重新分配，导致拖拽结果被覆盖。

实测证据（科室库房目录，storageKey `spd-department-warehouse-catalog-cols-v1`）：

- 存储宽度：`scope=230 productCode=160 productName=200 spec=160 manufacturer=150 source=138 status=139 updateTime=190`
- 实际渲染宽度：`119 / 104 / 263 / 177 / 218 / 56 / 56 / 133 / 104`

把 `productName` 存成 420 并刷新后，实际渲染仍是 263 —— 用户拖拽宽度在刷新后被重置。

代码注释声称「用户拖拽结果不会被按比例分配覆盖」，与实现不符。

### 修复方向

为每列增加持久化的「用户已手动调整」标记（`manual` / `userWidth`）。`effectiveWidths` 中：

- 手动列宽度固定为用户值，既不分配富余也不再压缩；
- 未手动列继续按权重共享剩余空间，保留 `hybrid` 的理想宽度调剂；
- 「恢复默认」清除手动标记，恢复自适应；
- 存量 localStorage 数据无标记时按未手动处理，向后兼容。

## 拣配记录筛选排布修复（本轮已完成）

`frontend/src/views/supply-chain/PickingRecordsView.vue`

表单同时挂载旧类 `hospital-query-grid` 与新类 `ulc-filter`，前者经 `main.css` 与
`responsive-medical.css`（`!important`）强制 `display: grid`，导致 `.ulc-filter-fields`
只占 1 个 262px 网格单元，10 个筛选条件纵向堆叠（表单高 797px），按钮被挤到下方。

改为与标准页一致的 `md-hospital-filter` 后：7 列 × 2 行，表单高 177px，
按钮与末行对齐并置于右侧。三套配色均验证通过。
