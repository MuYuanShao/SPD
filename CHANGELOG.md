# 修改记录

本文件用于记录每次分支迭代、代码修改、发布准备和重要配置调整。

## 记录规范

- 每次功能分支合并到 `main` 前，必须补充一条记录。
- 每次直接修改主控分支配置或文档时，也必须补充一条记录。
- 记录应说明修改日期、分支、修改内容、验证方式和负责人。

## 模板

```markdown
## YYYY-MM-DD - 分支名
- 修改内容：
- 影响范围：
- 验证方式：
- 负责人：
```

## 2026-08-24 - feature/batch-11-requirements

- 修改内容：①待审批目录与医院目录新增时商品编码改为选填：填写取填写值，留空取招采子编码回填，两者皆空自动生成 SPD+000001 自增编码（新增 ProductCodeService 共享服务，基于 sys_sequence 原子分配并跳过已被占用的编码；创建/重新提交/修改申请均生效，新增接口返回实际商品编码；审批通过同步医院目录逻辑保持不变）。②新增证照管理模块（V54 迁移 license_document 表 + /licenses 接口 + 前端 4 个页签：商品证照/供应商证照/厂家证照/合同管理，支持证照信息维护、附件上传/阅览/下载，附件存盘与 sys_attachment 记录）。③科室/货位管理：库房类型改为下拉（一级库/二级库/三级库）、关联科室支持回车/放大镜搜索科室表；维护货位弹窗货位类型改为下拉（整件货位/散货货位/试剂货位）、固定商品编码支持回车/放大镜搜索医院目录。④库存汇总查询改为按库房+商品聚合：数量=散货数量+在库定数包内散货数量，金额=数量×采购价。⑤新增打印模板调整菜单（V55 迁移 print_template 表 + /print-templates 接口 + 前端模板编辑器：字段勾选/排序/新增/修改、纸张预设与宽高设置），定数包标签打印（ZPL 与浏览器打印）读取模板配置生效。⑥一键启动与离线部署打包：新增 scripts/package-offline-deployment.ps1（打包后端 jar、前端构建产物、内置 JDK17、一键启动脚本与部署说明到 output/offline-bundle），后端支持 spd.web.static-dir 一体化托管前端（SpaWebConfig/SpaFallbackController，非 /api 路径放行）；package.json 新增 start:dev 与 package:offline 脚本。⑦采购管理智能补货改为独立事务，出库量取科室申请表（department_requisition/department_requisition_item 近 60 天已审批申领数量），不再取配送出库流水；与科室申领智能补货各自写入 purchase_replenishment_analysis 与 replenishment_smart_analysis。⑧科室请购：新增请购列表面板（可调整默认申领类型散货/定数包、数量与移除商品）；请购明细改为只展示当前日期向前推 15 天内有出库记录（已确认科室消耗）且未停用的商品，不再展示科室库房目录全量商品。⑨拣配配送：待拣配申领单展示部分拣配标记且二次拣配沿用原申领单号；可用定数包只展示一级库（中心库）库存；定数包标签与拣配记录中的定数包支持点击查看明细（新增 /operational-closure/picking/package-labels/{labelNo} 明细接口：来源批次/绑定去向/事件流水）。⑩补货任务/拣配配送/科室申领/科室消耗选择科室时默认带出科室所关联的库房（拣配配送仍限定一级库，关联库房为一级库时直接带出）。
- 影响范围：Flyway V54/V55；masterdata（ProductCodeService/ProductApprovalService/ProductService）、licenses、printing 新模块；InventoryService/PurchaseOrderService/QuotaTemplateService/OperationalDeliveryModule/OperationalClosureReadModel；前端新增证照管理/打印模板调整视图，科室/货位弹窗、库存工作台、科室申领、业务闭环视图、zebraBrowserPrint；RbacAuthorizationService；SecurityConfig/SpaWebConfig/SpaFallbackController；scripts/package-offline-deployment.ps1；package.json；相关测试。
- 验证方式：后端全量测试通过；前端 vue-tsc + vite 构建通过；verify-rbac-permissions 与 verify-vue-sfc-structure 通过；重启后端应用 V54/V55 后接口实测。
- 负责人：Admin

## 2026-08-24 - feature/batch-12-requirements

- 修改内容：①科室消耗业务改为先按定数包码/UDI/唯一码查询定位商品再登记消耗（新增 GET /operational-closure/consumptions/resolve 接口：依次匹配定数包标签号、UDI 码、唯一码并返回商品信息；消耗界面商品直选替换为查询码搜索 + 已定位商品回显）。②盘点管理改为"新增盘点表"流程：弹窗多选商品范围（高值耗材/可收费耗材/不可收费耗材/定数包，选中范围以集合标签展示）→ 确认后按范围生成盘点明细表（商品编码/商品名称/规格型号/厂家/单位/库存数量/盘点数量/差异数量，差异=库存-盘点前端实时计算）；新增 POST /inventory/stocktaking/sheets、GET/PUT /inventory/stocktaking/{no}/items 接口，复核时范围盘点明细按商品聚合（盘亏 FIFO 扣减、盘盈计入最近批次）生成库存事件；V56/V57 迁移（recall_event 增加 batch_id、stocktaking 增加 dept_name、明细 balance_id/batch_id/actual_qty/diff_qty 允许为空）。③修复收费耗材明细查询界面左右滑动导航轨道：滑块宽度改为可视/内容比例、位移按可视宽度换算，点击/拖拽以跳转后位置为锚点，消除回跳与比例错位。④召回与隔离业务动作改为先选商品→再选该商品批号（新增 GET /operational-closure/recalls/batches 批次选项）→填写数量与召回原因→提交确认时按所选批次扣减库存（新增 InventoryMovementService.isolateSpecificBatch，可用库存转隔离库存并写库存事件）。
- 影响范围：Flyway V56/V57；OperationalConsumptionModule/OperationalRiskModule/InventoryService/InventoryMovementService/SupplyChainSupport/OperationalClosureService/OperationalClosureController/InventoryController；前端业务闭环视图（科室消耗查询码搜索、召回批次选择）、库存工作台（盘点表流程）、收费耗材明细滚动轨道、inventory/operationalClosure API；OperationalRiskModuleTest 新增用例。
- 验证方式：后端全量测试通过；前端 vue-tsc + vite 构建通过；Playwright 冒烟测试通过；接口实测：定数包码/UDI 定位商品、批次召回扣减库存、盘点表生成/保存/差异计算全部 200 code=0。
- 负责人：Admin

## 2026-08-24 - fix/warehouse-dept-backfill

- 修改内容：修复库房/货位管理新增（编辑）库房弹窗中"关联科室"框无法回填科室的问题：原搜索框绑定的是临时关键字、点选科室后清空输入框导致选中科室不显示；改为组合框模式——输入框直接绑定并显示所选科室名称，输入时实时过滤科室表候选，回车/放大镜/聚焦均可打开候选列表；新增回归用例 tests/e2e/warehouse-dept-backfill.spec.ts（新增库房 → 关联科室搜索选点 → 断言回填、关键字过滤再选择）。
- 影响范围：WarehouseDialog.vue、tests/e2e/warehouse-dept-backfill.spec.ts。
- 验证方式：前端 vue-tsc + vite 构建通过；Playwright 回归用例实测通过（点选回填、关键字过滤选择均断言成功）。
- 负责人：Admin

## 2026-08-21 - feat/full-flow-extended-chain

- 修改内容：scripts/verify-full-flow.mjs 扩展为覆盖完整业务主链的 53 步端到端验证：新增医院目录（新品准入）→ 多步审批至最终通过（循环推进审批步骤）→ 定数包模板维护 → 库房商品绑定/科室库房目录维护 → 采购订单（创建→提交→审批→发送）→ 收货验收（exchange/isAgent 新字段回显）→ 库存三页签 → 定数包打包/按验收单部分30/全部60分配/确认10标签/打印 → 低值定数包唯一码/UDI 追溯记录 → 高值链路（收货→2唯一码→计费回传×2→收费明细→唯一码退出在库）→ 盘点(盘亏-3)/调价/召回 → 科室申领审批 → 字段管理/交易流水/UDI/工作台/operator01 权限回归；每轮自动生成全新商品/模板/单据，支持重复运行。
- 影响范围：scripts/verify-full-flow.mjs。
- 验证方式：脚本实测 53/53 全部通过（新增目录→审批→模板→目录→采购→收货→库存→打包→低值 UDI→高值计费→盘点/调价/召回→申领→查询面）。
- 负责人：Admin

## 2026-08-21 - fix/full-flow-ab-test

- 修改内容：全流程 A→B 端到端测试（scripts/verify-full-flow.mjs，46 步：采购订单→收货验收新字段→库存三页签→定数包打包/按验收单分配/确认/打印→高值收货/唯一码/计费回传/收费明细→盘点/调价/召回→库房商品绑定/科室目录/申领→字段管理/交易流水/UDI/工作台/权限回归）发现并修复：①定数包库存查询只统计待打印标签，标签打印后（available）从库存页签消失——改为统计 pending_print/available 两种在库状态。②种子账号 operator01（运营管理员）无任何角色导致零权限，3 条既有 UI 冒烟用例全部失败——V53 迁移补分配 operator 角色。③3 条 UI 冒烟用例默认账号改为 admin（保留环境变量覆盖），与打包分配用例一致。④收费耗材明细冒烟用例适配改版后列结构（改按 UID/唯一码列 strong 文本取唯一码并以此过滤，替代旧的 P-xxx 商品编码列位假设）。
- 影响范围：InventoryService.quotaPackageStock、V53 迁移、tests/e2e 三条用例、scripts/verify-full-flow.mjs。
- 验证方式：全流程脚本 46/46 通过；后端全量 487 用例通过、聚焦 Inventory+Receiving 39 用例通过；UI 冒烟 4 条全部通过。
- 负责人：Admin

## 2026-08-21 - fix/packing-allocation-smoke

- 修改内容：①修复定数安全量弹窗（QuotaSafetyDialog）关联库房搜索框 v-model 与 :value 同绑导致的 Vue 模板编译错误（开发模式模块加载 500，打包任务确认等定数包页面无法打开），改为选中库房后回填搜索框、编辑时回显已选库房。②新增 Playwright 冒烟用例 tests/e2e/packing-task-receiving-allocation.spec.ts 覆盖"按验收单分配散货"流程：自建收货单→审核入库→新建打包任务→页面查询验收单散货→部分分配 30→全部打包分配→分配明细与任务预占数量断言，可用散货量动态读取，支持重复/并发运行；package.json 新增 test:smoke:packing-allocation 脚本。
- 影响范围：定数包安全量弹窗组件、tests/e2e 目录、package.json。
- 验证方式：Playwright 冒烟用例单跑与 --repeat-each=2 并发跑均通过；前端 vue-tsc + vite 构建通过；开发模式模块编译 200。
- 负责人：Admin

## 2026-08-21 - feature/inventory-tabs-receiving

- 修改内容：①库存管理改为三个独立查询页签：库存汇总查询（库房/科室/商品编码/商品名称/规格型号/注册证号/单价/单位/数量/金额/厂家/供应商）、定数包库存查询（库房/科室/定数包编码/定数包名称/规格型号/注册证号/单价/单位/定数包数量/散货数量/金额/厂家/供应商，按库房+科室+定数包模板汇总在库标签并关联同商品散货余额）、唯一码查询（科室/库房/商品编码/商品名称/规格型号/注册证号/批号/批次/单价/单位/数量/金额/厂家/供应商/唯一码/UID码，经 inventory_batch_trace_code→udi_trace_code→inventory_batch→inventory_balance 关联在库唯一码）；三个页签各自独立数据源、独立分页与查询表单，后端新增 /inventory/quota-package-stock 与 /inventory/unique-code-stock 接口。②收货验收新增收货界面字段改为：收货库房、配送商、采购订单、收货类型（下拉选，选项取字段管理维护表 sys_field_option，键 receiving_type：正常收货/退货收货/换货收货/代理商直送）、是否代理商（勾选）、备注（V52 迁移 receiving_order 新增 receiving_type/is_agent 列并种子化字典选项；创建/修改/列表接口同步支持新字段）。
- 影响范围：Flyway V52、InventoryService/InventoryController、ReceivingOrderRequest/ReceivingOrderService、前端库存管理工作台与收货验收视图、相关 API 客户端与组合式函数、ReceivingOrderServiceTest。
- 验证方式：后端聚焦测试 InventoryServiceTest+ReceivingOrderServiceTest 39 用例通过、mvn compile 通过；前端 vue-tsc + vite 构建通过；重启后端应用 V52 后 API 实测登录、字段选项（4 项）、库存汇总/定数包/唯一码/收货列表接口均 200 code=0。
- 负责人：Admin

## 2026-08-20 - feature/batch-10-requirements

- 修改内容：①医院目录/待审批目录新增"采购包装数量"（V50 迁移），"换算系数"更名"中包装数量"（前后端标签与变更摘要同步）。②院区编码创建时自动生成 XQ+序号，前端不再填写。③定数包模板前后端取消"适用科室"，定数包名称自动生成为"商品名称+定数包"。④科室库房目录新增/批量弹窗：科室与商品支持回车+放大镜搜索（商品范围限医院目录、已维护目录不展示，新增 product-options 接口），关联库房自动带出科室库房关联关系（唯一库房自动选中）。⑤定数包模板选择商品时包内数量自动回填医院目录中包装数量（可编辑）。⑥打包任务确认支持按验收单号分配散货库存：部分分配剩余保持散货、不填数量即全部打包分配（V51 预占明细记录验收单来源）；分配限定任务商品与库房、数量须为每包数量整数倍并按比例增加打包数量，端到端验证（建单→部分30→全部60→确认生成10标签、来源合计100、出库事件-10/-30/-60）。⑦定数安全量新增维护改为：先展示现有全部定数包并搜索选定，再关联搜索库房（科室随库房带出），最后填写安全上下限。⑧智能补货算法以点击当天前移核算天数统计科室库房出库事件（inventory_event，事务化方法 computeDeptWarehouseOutbound，支持全院数据）。⑨修复科室请购页与收费耗材明细页乱码链接文字（?????? → 高值耗材申领/高值耗材计费操作）。⑩库存交易流水明细扩展（科室/库房/商品/规格/注册证号/批号/批次/单价/单位/数量/金额/厂家/供应商/定数包码或唯一码/UID码），查询字段扩充（科室/库房/商品编码/名称/批号/批次/厂家/供应商/时间段），按科室+发生时间排序。
- 影响范围：Flyway V50/V51、主数据/待审批目录/定数包/安全量/补货/打包任务/库存流水前后端、OperationalShortageModule 与 InventoryService/PackingTaskService 查询、若干测试。
- 验证方式：后端聚焦测试（ProductService/ProductApprovalService/SupplierService/QuotaTemplate/Campus/Shortage/Inventory/PackingTask 等 120+ 用例通过），前端 vue-tsc+vite 构建通过，浏览器抽查核心页面。
- 负责人：Admin

## 2026-08-20 - main

- 修改内容：新增一键启动文件 start-dev.ps1（检查 MySQL、启动后端与前端、输出访问信息，支持 -SkipBackend/-SkipFrontend 参数）与双击版 start-dev.bat（Windows PowerShell 5.1 兼容，含 UTF-8 BOM）。
- 影响范围：仓库根目录新增两个启动文件，不改变现有 npm 脚本与运行方式。
- 验证方式：使用 Windows PowerShell 5.1 实测一键启动，后端健康检查 UP、前端 HTTP 200、admin 登录成功。
- 负责人：Admin

## 2026-08-20 - feature/catalog-field-management

- 修改内容：供应商管理前后端新增"经营许可证号"字段（V49 迁移新增 supplier.business_license_no 列，列表新增列展示、新增/修改弹窗可维护）；医院商品目录与待审批目录选择供应商时自动回填该供应商的经营许可证号到"经营许可证号"字段（修改弹窗、编辑表单页、新增弹窗、审批明细内联编辑、重新提交表单共五处），两个 partner-options 接口供应商选项增加 businessLicenseNo 字段。

- 修改内容：医院商品目录（修改弹窗、编辑表单页）与待审批目录（新增弹窗、审批明细内联编辑、重新提交表单）中选择厂家时，自动回填厂家管理表中的生产许可证号到“生产许可证号”字段；两个 partner-options 接口的厂家选项增加 licenseNo 字段。
- 修改内容：医院目录编辑表单页（ProductFormView）的生产厂家、供应商字段改为下拉选（与修改弹窗一致），选项取厂家/供应商主数据表。

- 修改内容：供应商管理、厂家管理合并为单一菜单"供应商厂家管理"，页面用页签切换（供应商/厂家），每个页签独立分页展示（每页条数可调、翻页、跳页），旧路由 /features/supplier-management 与 /features/manufacturer-management 自动重定向到合并页对应页签。
- 修改内容：合并页权限兼容旧权限码（拥有 supplier-management 或 manufacturer-management 任一菜单权限即可访问合并页），后端 MySQL 表与接口不变，仅前端调用既有 /master-data/suppliers、/master-data/manufacturers 接口。
- 修改内容：供应商/厂家列表补充分页控件（此前仅显示默认前 20 条，无法翻页）。

- 修改内容：待审批目录重复校验改为规则驱动（V48 新增 sys_validation_rule 存储"商品名称+规格型号+生产厂家+供应商+注册证号"组合），校验命中时前端弹窗提示"该商品目录已存在！"；新增表单打开时重置，不再带出历史数据。
- 修改内容：修复医院目录"信息变更"审批单详情 500（product 查询中误引用 pending 表别名的 supplier_name，共三处 SQL 别名错误）。
- 修改内容：修复医院目录表单页编辑保存时误弹"定数管理改为否"确认框（仅在实际由是改否时提示）。
- 修改内容：医院目录详情页改为单列、规整的字段网格布局，字段完整展示。
- 修改内容：医院目录"修改"弹窗改为分区布局（基础信息/价格采购与分类/资质信息/业务属性），表单主体可滚动、保存/取消固定在底部常显，三列自适应网格，修复原弹窗保存按钮在屏幕外、字段无分组的问题。
- 修改内容：待审批目录"新增"弹窗同样改为平铺分区布局（申请信息/商品基础信息/价格采购与分类/资质与仓储/业务属性），表单主体可滚动、提交审批/取消固定在底部常显，修复原弹窗提交按钮在屏幕外约 624px、整窗滚动的问题。
- 修改内容：待审批目录"新增"弹窗、审批明细内联编辑与"修改后重新提交"弹窗中的生产厂家、供应商字段改为下拉选，选项取厂家/供应商主数据表（新增 /pending-product-applications/partner-options 接口，权限对齐待审批目录），保留当前值兜底与停用标识。
- 修改内容：Vite 开发服务器在 Windows 下启用轮询文件监视（usePolling），避免编辑文件时 fs.watch 目录改名竞态导致 dev server EBUSY 崩溃。
- 修改内容：供应商新增/编辑弹窗的"供应商类型""供应商等级"改为字典下拉选，不再人工填写。
- 修改内容：新增字段管理模块（sys_field_option 字典表 + /system/field-options 接口 + 系统管理下"字段管理"页面），支持所有表格下拉选字段的选项新增、修改、停用与删除，并接入 RBAC 权限。
- 影响范围：待审批目录创建/导入校验、医院目录编辑与详情、供应商弹窗、系统管理菜单与权限、Flyway V48。
- 验证方式：后端全量测试 487 通过（含 ProductApprovalServiceTest 30 项）；前端 vue-tsc + vite 构建通过；Playwright 浏览器逐项验证五项需求；verify:vue-sfc-structure / verify:rbac-permissions / verify:text-encoding 通过。
- 负责人：Admin

## 2026-06-18 - main

- 修改内容：建立 Git 主控分支规则，补充仓库忽略规则，新增修改记录文件。
- 影响范围：仓库版本管理、迭代记录流程。
- 验证方式：确认 Git 状态与初始提交。
- 负责人：Codex

## 2026-06-18 - codex/ui-medical-tech-warm

- 修改内容：优化定数包维护页面 UI 排版，强化医疗青绿色、科技感背景层和暖色辅助氛围。
- 修改内容：按管理后台紧凑模式收敛定数包页面，隐藏统计卡、抬头说明、查询说明标题和顶部工具展示区，突出查询栏与明细数据。
- 修改内容：同步收紧收货验收页面，隐藏说明、统计和顶部工具栏，保留查询栏与验收单明细表。
- 修改内容：收货验收详情弹窗改为紧凑布局，新增明细查询输入框与查询按钮，支持回车触发查询，并补充后端 `keyword` 过滤。
- 影响范围：前端全局顶部栏、定数包页面、收货验收页面、收货验收详情弹窗、收货明细分页查询接口。
- 验证方式：前端构建、后端编译与浏览器截图核验。
- 负责人：Codex
