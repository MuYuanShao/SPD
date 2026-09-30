
> 2026-09-30更新：下述两个目录的流程配置冲突已由V86迁移及动态审批修复解决。真实MySQL持久化8项全部通过，另有审批生命周期1项通过。原文保留核查时结论，最新结果见 [动态审批修复记录](2026-09-30-single-catalog-approval.md)。

# 前端操作与MySQL落库核查（2026-09-28—29）

## 结论

六类模块均存在真实的前端API、Controller、服务层及MySQL写入链路，不是仅修改浏览器中的模拟数据。但是，当前环境不能笼统判定“全部保存成功”：待审批目录及医院目录的新品提交因初审流程配置冲突而失败，事务不能成功提交。

| 模块 | 实际业务落点 | 本次结果 |
|---|---|---|
| 待审批目录 | pending_product_application、审批路由、审批动作、audit_log；最终通过时更新product | 价格调整提交与审批通过验证；新品提交被初审配置冲突阻断 |
| 医院目录 | 新增/修改先生成pending_product_application；通过后写product | 已验证采购价由1经审批改为2；新品新增提交被同一配置问题阻断 |
| 库房/货位 | warehouse、warehouse_product_binding、warehouse_location，以及关联科室目录 | 新增、修改、商品绑定、货位新增修改软删除通过真实SQL回读验证 |
| 科室管理 | sys_dept；科室库房关联使用warehouse.dept_id | 新增、修改、软删除通过真实SQL回读验证 |
| 供应商厂家管理 | supplier、manufacturer | 两者新增、修改、停用均通过；停用是status=0，不是物理删除 |
| 定数包模块 | quota_package_template、quota_package_template_item、quota_safety_stock、quota_packing_task、quota_package_label、quota_package_label_source、quota_package_event、inventory_balance、inventory_event等 | 模板创建及新版本、启停、安全量、任务创建确认、打印记录、解包、重算、取消、终止通过 |

## 已确认的阻断问题

当前同一节点 `pending-product-catalog / initial-review` 存在两条启用的全局流程：

| flow_id | scope_type | scope_id | status |
|---|---|---|---|
| 1 | global | default | 1 |
| 14 | global | ai-e2e-20260827 | 1 |

`CatalogApprovalRouteService` 对两条同等优先级的适用流程拒绝任意选择，报错：

> 审批流程配置冲突：initial-review

按生产构造参数调用 `ProductApprovalService.createApplication` 和 `ProductService.createHospitalProduct`，均重现该错误。这意味着用户可能点击保存、请求也到了后端，但仍不能成功落库；不能只凭请求发出或SQL中存在INSERT就判断保存正常。

价格调整使用独立的 `batch-price-adjustment / price-adjustment-approval` 流程，本次实测成功：审批前product采购价为1，审批后为2，申请状态变为approved。

需要先确认业务应采用哪条初审流程，再停用另一条或调整适用范围。本次只核查，未修改或删除审批流程。

## 验证方法与证据边界

1. **代码链路**：核对前端操作函数、API客户端、`/api`基础路径、认证头、Controller写接口及JdbcTemplate写表语句。
2. **真实浏览器读取**：未Mock读取接口，11个页面/子页面请求均返回HTTP 200、业务code=0。包括待审批、医院目录、库房、科室、供应商、厂家、定数模板、安全量、打包任务、标签和散货快照。
3. **真实MySQL写入**：新增 `BusinessPersistenceMysqlAuditTest`，调用实际服务后直接SELECT核对字段和状态。完整核查8个场景，6个通过、2个因上述真实配置冲突报错；随后补充的库房与定数包操作验证2项通过。
4. **不污染业务数据**：每个测试使用单连接未提交事务，成功或失败最终都执行rollback；没有Mock数据库，没有提交测试业务记录，没有调用真实打印机。
5. **验证限制**：这不是每个按钮都经浏览器HTTP提交并永久保存的全量端到端验收；写入实测在服务层真实数据库事务内完成，使用系统操作人，未遍历各普通角色权限。导入文件格式、每一种审批状态及所有字段组合未逐一提交验证。

## 页面上哪些操作不会写库

- 查询、筛选、分页、查看、横向滚动、勾选待操作行：读取或页面状态操作。
- 医院目录显示列、列顺序等界面设置：当前在前端状态中调整，不等同于更新商品主数据。
- 待审批目录“列设置”按钮目前没有绑定操作处理，不能当作已经保存设置。
- 导出、模板下载：生成文件，不代表写入业务数据。
- 可打包散货快照：读取库存余额的聚合结果；调整显示列或每页条数不会改库存。
- 标签打印：后端打印次数、状态、事件能够落库，但这不能证明纸张已物理打印成功；本次未连接打印机。

## 回滚核对

关键表在核查前后数量一致：product 44、pending_product_application 224、warehouse 12、sys_dept 16、supplier 6、manufacturer 6、quota_package_template 16。测试没有保留新业务记录；数据库自增ID允许因回滚产生正常间隙。

## 文件与复核入口

- 测试：`backend/src/test/java/com/hospital/spd/audit/BusinessPersistenceMysqlAuditTest.java`
- 浏览器真实读取结果：`output/business-api-reads.log`
- 完整数据库核查结果：`output/business-persistence-final.log`
- 补充核查结果：`output/business-persistence-extended.log`
- 前端链路：`frontend/src/api/masterData.ts`、`pendingProductApplications.ts`、`quotaPackages.ts`及对应composables。

复核时显式设置 `SPD_PERSISTENCE_AUDIT=true` 后运行该测试类；默认测试套件不启用此真实数据库核查。该测试会真实写入临时事务并回滚，当前审批配置未修复时，两个新品提交用例应继续报错，不应为了让测试通过而跳过审批路由校验。
