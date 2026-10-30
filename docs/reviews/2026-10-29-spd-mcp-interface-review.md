# SPD-PR / SPD-MCP-UI 接口审核（2026-10-29）

审核对象为两个本地仓库当前代码，快照95个文件、8156行，并追查相关审批、序列、证照删除及本机Java17传输实现。三个独立视角全部完成。
本次静态审核未运行双端联调、测试或构建，未修改任何一端业务代码。

## 接口覆盖结论

| 数据类型 | 当前方向/接口 | 核实结果 |
|---|---|---|
| 商品目录 | MCP-UI → SPD-PR `/api/mcp/receive` | 进入待审批目录，processed不代表院内审批通过 |
| 证照 | 同一下发信封及licensesOnly，附件+稳定sourceLicenseId | 已有下发、资质审批及版本退休机制，发现改号附件缺陷 |
| 厂家/供应商 | SPD-PR → MCP-UI `/api/integration/instances/{id}/upload/{kind}` | 已实现 |
| 采购计划 | 两端各自本地计划业务 | 未发现同步、映射或状态回写 |
| 采购订单 | SPD-PR → MCP-UI `/api/integration/instances/{id}/orders` | 进入独立spd_record对接区，不等于MCP原生采购订单 |
| 收货/入库/库存 | SPD-PR → MCP-UI `/phase2/receiving`、`/phase2/inbound`、`/phase2/inventory` | 已实现，第二期开关默认关闭 |
| 结算数据 | MCP-UI `/api/settlements`只读自身biz_settlement | 未发现SPD结算同步；入库统计不能替代结算 |
| 采购退货 | MCP-UI第二期接收接口 | 按现有约定尚无SPD真实上传来源，不判为遗漏BUG |

## 已核实逻辑问题

2项高风险、3项中风险。

### F1 待审批证照改号遗留旧附件并一并准入

风险：high；来源：blind-hunter+edge-case-hunter

- [McpReceiveService.java:135](D:/SPD-PR/backend/src/main/java/com/hospital/spd/integration/McpReceiveService.java:135)
- [ProductApprovalService.java:1346](D:/SPD-PR/backend/src/main/java/com/hospital/spd/masterdata/service/ProductApprovalService.java:1346)

同一sourceLicenseId在审批前从编号A改B，待审批附件查询仍按新license_code=B查旧记录，未删除A；稳定身份意图却覆盖为B。

licensesComplete按sourceLicenseId判断，A与B同ID不会生成旧附件unlinked动作；审批提升全部deleted=0的申请附件，A和B都进入院内商品；后续已批准版本只跟踪B，A成为无法正常退役的旧来源证照。

### F2 MCP原生采购计划并发转单可超过计划量

风险：high；来源：edge-case-hunter

- [PurchaseRepository.java:250](D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/purchase/PurchaseRepository.java:250)
- [PurchaseRepository.java:437](D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/purchase/PurchaseRepository.java:437)
- [PurchaseRepository.java:468](D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/purchase/PurchaseRepository.java:468)

两次并发转换在无计划行锁的情况下都读取相同计划和剩余量，随后各自生成全额采购订单及配送明细。

PurchaseService只先普通读状态并开启事务；findPlanSeed和remainingPlanQty均未锁父计划。ID序列锁发生在剩余量计算之后，后续UPDATE计划不重新校验，不能防止第二事务沿用旧剩余量。此问题属于MCP本地计划转单接口，不冒充已实现的SPD计划同步。

### F3 商品重新启用状态未传入审批目标

风险：medium；来源：blind-hunter

- [McpReceiveService.java:111](D:/SPD-PR/backend/src/main/java/com/hospital/spd/integration/McpReceiveService.java:111)
- [CatalogApplicationSnapshotService.java:53](D:/SPD-PR/backend/src/main/java/com/hospital/spd/masterdata/service/CatalogApplicationSnapshotService.java:53)

下发active仅进入临时JSON；过滤PendingProductApplicationRequest字段时status被去掉，现有停用商品被作为信息变更处理，目标状态仍沿用0。

DTO没有status/targetStatus；普通信息变更调用snapshotService.write时explicitTargetStatus为null。仅启用无其他变化会被无差异校验拒绝；连同其他资料修改批准后仍停用。院内原生启停流程可绕过，故为中风险。

### F4 历史软删除证照持续占用十份下发额度

风险：medium；来源：blind-hunter

- [IntegrationService.java:143](D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/integration/IntegrationService.java:143)
- [LicenseRepository.java:191](D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/license/LicenseRepository.java:191)

证照选择包含已软删除记录且保留product_id；在判revoked前对全部licenseRows执行size>10限制。

累计创建删除超过十份后，即使只有一份有效证照，商品及独立证照下发均失败；已成功撤销的历史墓碑没有清理/checkpoint。不是反对单次十份协议上限，而是历史记录永久挤占当前可传集合。

### F5 双方HTTP响应正文读取无超时边界

风险：medium；来源：blind-hunter

- [SpdTransport.java:33](D:/SPD-MCP-UI/server/src/main/java/com/spd/mcp/api/integration/SpdTransport.java:33)
- [McpUploadJob.java:154](D:/SPD-PR/backend/src/main/java/com/hospital/spd/integration/McpUploadJob.java:154)

请求设置15秒timeout，但send使用ofInputStream后在调用线程阻塞readNBytes；响应头已到达后，正文停传不关闭连接可无限等待。

已核对本机Java17源码HttpResponse.BodyHandlers.ofInputStream说明及MultiExchange.cancelTimer：响应对象返回时正文可未收完；代码后续流读取没有deadline。SPD上传为synchronized且串行扫描，单次卡住会阻止后续轮次、失败落库及重试；需运维恢复，因此中风险。

## 测试覆盖缺口

- F6 乱序下发回执缺少版本保持回归。现有跨实例回执测试在applyReceipt前拒绝；真实双端脚本按顺序完成下发，未覆盖新回执先完成、旧回执后完成。 需断言较新商品映射与完整证照绑定集合保持不变。当前有版本保护，未证明其失效；这是测试缺口。
- F7 计划转订单缺少数量和金额落库断言。双端验收将计划改为数量12单价2后，只断言生成的订单ID出现在列表，没有核对订单金额24及配送明细数量12。 mock服务测试不执行仓储转换；需补真实数据库/API断言，防止成功建单却转错数量金额。此缺口独立于R8并发超量。
- F8 结算查询缺少租户隔离和财务字段测试。已实现结算GET的租户/删除过滤与数量、单价、金额、批次映射未找到对应自动测试。 现有dual_system脚本不请求settlements，单元测试也未覆盖。需两个租户及deleted记录夹具验证精确返回值；这不是已证明越权或金额计算错误，也不是要求补不存在的结算写接口。

## 能力未覆盖项

- F9 采购计划尚无双端同步链路。未找到来源计划身份映射、计划上传下发和审批状态回写。第一二期现有契约没有承诺采购计划对接，应视为用户本次检查范围未覆盖，而非已实现接口的BUG。
- F10 结算数据尚无双端同步链路。入库金额、采购统计不等于供应商结算。未发现结算单及明细、确认/撤销状态同步或双方结算身份映射；属于未覆盖能力，非现有第一二期契约违约。

## 建议处置

先修证照稳定身份替换和本地计划并发转单，再处理重新启用、历史撤销证照集合与响应正文读取期限。为修复增加对应真实双端/数据库回归。采购计划和结算对接需单独明确权威系统、传输方向、稳定来源ID、版本、金额/税口径、确认与撤销流程，不能复用订单或入库报表假装已接通。

本轮0项自动修复，10项登记待处理；11项原始发现合并1项同根因重复，0项拒绝，0个审查视角失败。原始与归并发现、证据、处置路径保存在本轮triage文件。