

## Deferred from: code review (2026-10-28) — batch 1 inventory

- [high] 盘点遗漏预占实物导致虚增库存 — backend/src/main/java/com/hospital/spd/supplychain/service/InventoryService.java:412；10件库存预占8件后账面仅2件，实盘10件形成盘盈8件；仅预占未生成包标签时 requireStocktakingSnapshot 不阻止，最终 available10+locked8=18。（既有问题，审核未修改代码）
- [high] 范围盘盈借用全院最新批次 — backend/src/main/java/com/hospital/spd/supplychain/service/InventoryService.java:575；A库原有商品盘盈，B库更晚收货，则A库盘盈被记入B库批次，串改供应商、效期和成本归属。（既有问题，审核未修改代码）
- [high] 空需求请求成功并消耗补货分析 — backend/src/main/java/com/hospital/spd/supplychain/service/PurchaseOrderService.java:339；合法analysisAdjustments配空items可把分析标为demand_created，却没有需求明细，且正常重生成会被状态校验拒绝。（既有问题，审核未修改代码）
- [high] 补货分析调整与生成需求可不一致 — backend/src/main/java/com/hospital/spd/supplychain/service/PurchaseOrderService.java:364；可调整分析商品A却生成商品B需求并关闭分析；前端会构造一致数据，但直接API调用无服务端保护。（既有问题，审核未修改代码）
- [high] 过期或日期矛盾批次可验收为可用库存 — backend/src/main/java/com/hospital/spd/supplychain/service/ReceivingOrderService.java:822；创建及审核均无日期关系检查，receiveAvailable 增加 available_qty；下游FIFO却排除过期批次，形成可用库存和可发库存口径不一致。（既有问题，审核未修改代码）
- [medium] 同商品多采购行校验错行 — backend/src/main/java/com/hospital/spd/supplychain/service/ReceivingOrderService.java:857；采购允许同商品多行，界面携带行ID；第一行已收完、第二行待收时，第二行合法验收仍可能被第一行零余额拒绝。（既有问题，审核未修改代码）
- [medium] 切换临时收货保留采购明细身份 — frontend/src/views/supply-chain/ReceivingAcceptanceView.vue:331；导入采购明细后改临时收货并选供应商，提交仍携带旧行ID，后端 insertReceivingItems 匹配 null 采购单失败。（既有问题，审核未修改代码）
- [medium] 零合格数量高值UDI阻断混合验收 — backend/src/main/java/com/hospital/spd/supplychain/service/ReceivingOrderService.java:674；混合单另有合格行使流程进入校验；完全不合格的高值行无需入库，却阻止其他行审核。（既有问题，审核未修改代码）
- [medium] 零库存商品无法录入范围盘盈 — backend/src/main/java/com/hospital/spd/supplychain/service/InventoryService.java:416；发现账外实物无法加入范围盘点；全空范围仍创建零明细单，明细读取报不存在或无明细。（既有问题，审核未修改代码）
- [medium] 批次调价缺少真实余额汇总验证 — backend/src/main/java/com/hospital/spd/common/service/InventoryEventService.java:128；测试缺口，不是已证明的生产计算错误；需真实数据库验证 available/locked/in_transit/isolated 的合计及 value_change。（既有问题，审核未修改代码）


## Deferred from: code review (2026-10-28) — batch 2 operational flows

- [high] 消耗、缺货及红冲列表未限制科室数据范围 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalClosureReadModel.java:128；HTTP权限只校验对应功能访问权；部门范围只在申领等其他查询中追加，不会自动限制本分支。具有功能权限但仅本科室数据范围的用户可取得其他科室记录。（既有问题，本轮未修改代码）
- [high] 旧补货分析及任务生成绕过科室范围 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalShortageModule.java:96；旧 /shortage/smart-analysis 接口仍公开并在OperationalClosureView调用；不经DepartmentRequisitionAccessService，能够越权读取出库库存并保存分析或其他科室任务。（既有问题，本轮未修改代码）
- [high] 反消耗未冲减智能申领的实际消耗统计 — backend/src/main/java/com/hospital/spd/supplychain/service/DepartmentRequisitionSmartService.java:223；消耗100→反消耗100→再次真实消耗100，当前库存0；历史负向事件合计200，推荐200而实际有效消耗100。反消耗原负向事件按不可变流水规则仍保留。（既有问题，本轮未修改代码）
- [high] 消耗扫码解析未检查科室权限 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalConsumptionModule.java:242；仅有科室范围的消耗页面用户，知道其他科室的码即可通过 /consumptions/resolve 读其信息；真正消耗时才做assertDepartmentScope，不会保护先前解析响应。（既有问题，本轮未修改代码）
- [high] 桌面定数包拣配及签收未检查来源批次效期 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalDeliveryModule.java:485；打包时有效、库存放置至过期的包仍能通过桌面拣配并签入科室可用库存；扫码消耗时才被按批次扣库拦截。移动端MobilePickingService已检查source.expired，桌面同业务路径缺少等价校验。（既有问题，本轮未修改代码）
- [medium] 过期散货批次阻断后续有效批次拣配 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalDeliveryModule.java:262；有1件过期和100件有效库存时，申请1件先选中过期批次并回滚，无法自动使用有效库存；availableLooseStock也展示过期库存。隔离过期库存可绕过。（既有问题，本轮未修改代码）
- [medium] 共享待结算单反消耗后的剩余金额缺少数据库验证 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalConsumptionModule.java:201；这是验证缺口，未证明现有重算错误。应在隔离MySQL中创建同一待结算单包含两笔消耗，撤销其一后断言剩余明细与total_amount精确值。（既有问题，本轮未修改代码）
- [maybe-false] 重复分析未考虑待签收配送及未完成申领 — backend/src/main/java/com/hospital/spd/supplychain/service/DepartmentRequisitionSmartService.java:228；状态和算式确可产生重复建议，但代码明确采用近N天实际消耗减当前库存，现有上下文未明确在途是否属于建议扣减项，不能直接定性为已确认BUG。 待确认：需确认补货建议是否必须抵扣已批准未完成申领/在途配送；若是，需定义不重复计量口径并做跨分析集成复现。（既有问题，本轮未修改代码）
- [maybe-false] 按批次扣库任取一个货位余额 — backend/src/main/java/com/hospital/spd/supplychain/service/InventoryMovementService.java:165；库表模型允许货位；但当前应用写入inventory_balance的入口均新建无货位余额，未找到能在正常现行流程产生多货位余额的入口，触发前提尚未证实。 待确认：检查实际数据库是否存在同库同商品同批次的非空location_id余额及合法历史导入来源；存在时以真实数据复现。潜在影响等级medium，未验证。（既有问题，本轮未修改代码）


## Deferred from: code review (2026-10-28) — batch 3 trace and recall

- [high] 召回范围遗漏预占、待打印及配送中定数包库存 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalRiskModule.java:198；100件同批次中20可用、80已预占时，召回仅隔离20并返回成功；80仍可确认打包。已确认待打印包不在余额，之后可打印配送；配送中包也可签收恢复可用。PackingTaskService确认打包只检查效期，不检查recall_event；打印与签收同样没有召回拦截。（既有问题，本轮未修改代码）
- [high] 高值召回隔离未同步单件追溯身份 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalRiskModule.java:136；召回成功后，余额已在中心库隔离，唯一码却仍在旧科室显示signed/in_stock/patient_bound；UDI隔离筛选与时间线遗漏该召回，bindPatient仍接受signed/in_stock身份。不能据此声称已成功计费，余额扣减仍有库存保护。（既有问题，本轮未修改代码）
- [medium] 跨库召回过期库存被普通出库效期规则阻断 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalRiskModule.java:142；召回候选查询包含过期批次，但转库扣减查不到该批次，报库存不足并回滚；本地isolate不转库可作为绕过，但召回流程本身失败。（既有问题，本轮未修改代码）
- [medium] 新定数包追溯先于来源写入导致批次效期留空 — backend/src/main/java/com/hospital/spd/supplychain/service/PackingTaskService.java:243；首次ensureTrace查询不到来源，写入batch_no/expire_date为NULL；之后关联来源时再次ensureTrace直接返回，transitionLabel亦不更新批次效期。UDI列表/详情从tc读这些字段，按批次查询会漏新包。来源关系仍在quota_package_label_source，可从包详情核对。（既有问题，本轮未修改代码）
- [medium] 追溯时间线按业务序号排序导致事件倒序 — backend/src/main/java/com/hospital/spd/supplychain/service/UdiTraceabilityService.java:106；正常先绑定患者后计费，页面却把计费放在绑定之前；包消耗→反消耗→再次消耗时，两次消耗都会排在反消耗前。前端原样v-for展示timeline，没有重排。（既有问题，本轮未修改代码）
- [medium] 已签收定数包召回的数量守恒缺少数据库验证 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalRiskModule.java:187；这是测试缺口，不是已确认当前算法错误。需用余额10含signed包5的数据断言预览散货5+包5=10，召回后可用0隔离10且无额外入库，包状态void。（既有问题，本轮未修改代码）


## Deferred from: code review (2026-10-28) — batch 4 settlement and integration

- [high] 普通配送签收入库未纳入日结汇总 — backend/src/main/java/com/hospital/spd/supplychain/service/DailyInventorySummaryService.java:19；已有日结基线后，空科室库签收10件散货，新日结键inbound=0、closing=0；随后消耗可产生负结存。assertFormula只检查同一套不完整分类算式，仍会通过。高值签收使用已收录的warehouse_transfer_in，不属于此问题。（既有问题，本轮未修改代码）
- [high] 历史日结重算使用当前批次价改写历史估值 — backend/src/main/java/com/hospital/spd/supplychain/service/DailyInventorySummaryService.java:128；过去结存20件单价10，今日批次调价12后重算过去日期，unit_price被覆盖为12，监管历史库存金额从200变240。generateThrough遇旧calculation_version会自动regenerateRange，不能只依赖未调用的手工重算入口。报表inventoryAmount直接使用日结unit_price。（既有问题，本轮未修改代码）
- [medium] 发票关联的待确认结算单阻断反消耗删除 — backend/src/main/java/com/hospital/spd/supplychain/service/OperationalConsumptionModule.java:225；supplier_invoice.settlement_id外键为默认RESTRICT（V28:23及V100:24），所以合法登记关联发票后，反消耗会触发外键异常并整笔回滚。不是部分库存已恢复；事务会回滚。应明确有发票时走何种撤销/解除关联流程或在绑定阶段限制状态，不能直接删空账单。（既有问题，本轮未修改代码）
- [medium] 日结跨午夜事件归属缺少真实数量验证 — backend/src/main/java/com/hospital/spd/supplychain/service/DailyInventorySummaryService.java:99；验证缺口，不是当前时间区间已有错误。应给期初10、午夜前消耗2、午夜整消耗3，断言相邻两日(期初,消耗,结存)分别(10,2,8)和(8,3,5)，防止<误改为<=却仍通过同源算式校验。（既有问题，本轮未修改代码）


## Repair follow-up (2026-10-29)

原四批23项逻辑问题已完成对应修正并复核；4项验证缺口已补真实MySQL回归，B2-F8/B2-F9保留待确认。详见 docs/reviews/2026-10-29-inventory-audit-fixes.md。AI外部服务冒烟超时单列，不计为通过。


## Deferred from: code review (2026-10-29) — SPD-MCP-UI integration

- [bug/high] 待审批证照改号遗留旧附件并一并准入 — D:/SPD-PR/backend/src/main/java/com/hospital/spd/integration/McpReceiveService.java:135；licensesComplete按sourceLicenseId判断，A与B同ID不会生成旧附件unlinked动作；审批提升全部deleted=0的申请附件，A和B都进入院内商品；后续已批准版本只跟踪B，A成为无法正常退役的旧来源证照。
- [bug/high] MCP原生采购计划并发转单可超过计划量 — D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/purchase/PurchaseRepository.java:250；PurchaseService只先普通读状态并开启事务；findPlanSeed和remainingPlanQty均未锁父计划。ID序列锁发生在剩余量计算之后，后续UPDATE计划不重新校验，不能防止第二事务沿用旧剩余量。此问题属于MCP本地计划转单接口，不冒充已实现的SPD计划同步。
- [bug/medium] 商品重新启用状态未传入审批目标 — D:/SPD-PR/backend/src/main/java/com/hospital/spd/integration/McpReceiveService.java:111；DTO没有status/targetStatus；普通信息变更调用snapshotService.write时explicitTargetStatus为null。仅启用无其他变化会被无差异校验拒绝；连同其他资料修改批准后仍停用。院内原生启停流程可绕过，故为中风险。
- [bug/medium] 历史软删除证照持续占用十份下发额度 — D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/integration/IntegrationService.java:143；累计创建删除超过十份后，即使只有一份有效证照，商品及独立证照下发均失败；已成功撤销的历史墓碑没有清理/checkpoint。不是反对单次十份协议上限，而是历史记录永久挤占当前可传集合。
- [bug/medium] 双方HTTP响应正文读取无超时边界 — D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/integration/SpdTransport.java:33；已核对本机Java17源码HttpResponse.BodyHandlers.ofInputStream说明及MultiExchange.cancelTimer：响应对象返回时正文可未收完；代码后续流读取没有deadline。SPD上传为synchronized且串行扫描，单次卡住会阻止后续轮次、失败落库及重试；需运维恢复，因此中风险。
- [verification_gap/medium] 乱序下发回执缺少版本保持回归 — D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/integration/IntegrationService.java:184；需断言较新商品映射与完整证照绑定集合保持不变。当前有版本保护，未证明其失效；这是测试缺口。
- [verification_gap/medium] 计划转订单缺少数量和金额落库断言 — D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/purchase/PurchaseRepository.java:250；mock服务测试不执行仓储转换；需补真实数据库/API断言，防止成功建单却转错数量金额。此缺口独立于R8并发超量。
- [verification_gap/medium] 结算查询缺少租户隔离和财务字段测试 — D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/settlement/SettlementRepository.java:22；现有dual_system脚本不请求settlements，单元测试也未覆盖。需两个租户及deleted记录夹具验证精确返回值；这不是已证明越权或金额计算错误，也不是要求补不存在的结算写接口。
- [capability_gap/medium] 采购计划尚无双端同步链路 — D:/SPD-PR/backend/src/main/java/com/hospital/spd/integration/McpUploadJob.java:44；未找到来源计划身份映射、计划上传下发和审批状态回写。第一二期现有契约没有承诺采购计划对接，应视为用户本次检查范围未覆盖，而非已实现接口的BUG。
- [capability_gap/medium] 结算数据尚无双端同步链路 — D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/settlement/SettlementController.java:19；入库金额、采购统计不等于供应商结算。未发现结算单及明细、确认/撤销状态同步或双方结算身份映射；属于未覆盖能力，非现有第一二期契约违约。


## SPD-MCP-UI repair follow-up (2026-10-29)

F1—F5已修复并完成真实双端102项验收；F7计划转单数量金额缺口已补真实落库断言。F6乱序回执及F8原生结算查询的专项测试缺口继续登记，不冒充已验证。F9/F10采购计划及结算对接已在测试通过后制定方案，尚未实现。详见 docs/reviews/2026-10-29-spd-mcp-interface-fixes.md 和 docs/plans/2026-10-29-mcp-purchase-plan-settlement-integration.md。
