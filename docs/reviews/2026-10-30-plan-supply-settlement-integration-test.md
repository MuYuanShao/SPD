# 采购计划、供货回传与结算上传实测

后续更新：下述供货阻断已完成开发及隔离环境验收，详见 [供货与收货闭环验收记录](2026-10-30-mcp-supply-receiving-acceptance.md)。本文保留修复前的测试证据。

日期：2026-10-30。当前代码基准 f76c323d。使用自建 MySQL 和专属双端 API / 页面测试，没有修改应用代码或业务数据库。

## 结论

| 用户要求的链路 | 结果 | 实际证据 |
| --- | --- | --- |
| SPD 采购计划上传 MCP | **通过** | JH202610300001 接收成功，1 条商品明细、数量 12 支；approved revision=2，SPD 转单后 executed revision=3 |
| MCP 供货后下发 SPD | **阻断，未打通闭环** | 原生配送保存返回 HTTP 403 / PERMISSION_DENIED / 无操作权限；上传的 SPD 单据也尚未接入原生供货和发货回传 |
| SPD 结算单及明细上传 MCP | **通过** | JS2026103000001 接收成功，1 条明细，3 支 × 1.2345，来源金额及头合计均为 3.7035 |

不能将“上传计划可查询”表述为“MCP 已能据此供货并回传”。本次没有完成 MCP 供货出库，因而没有供货成功后的回传实测成功结论。

## 一、采购计划上传

通过真实目录准入创建商品 ROUND_41819101，采购价 1.2345，单位“支”。SPD 正常创建采购需求 XQ202610300001 → 提交与审核 → 生成并批准计划 JH202610300001。

MCP 实际查询 phase3/purchase-plans 收到 sourceId=1、sourceStatus=approved、revision=2；明细 plannedQuantity="12"，sourceLineId="1"，未记录估价保留 null。SPD 后续执行计划生成 CG20261030001 并正常提交、审批、发送后，MCP 收到计划 revision=3 / executed，convertedOrderNos 包含 CG20261030001。现有订单上传也收到 CG20261030001 及来源计划关联。

实际页面位于“采购对接 → 医院计划与结算”。MCP 本地 /purchase-plans 和 /purchase-orders 中没有该 SPD 计划及订单，符合当前独立对接区的实现，但也意味着它们尚不能进入 MCP 原生供货动作。

## 二、供货回传阻断

为区分来源计划与本地供货能力，另以 MCP 自带隔离演示计划 PP-DEMO-9001 创建原生订单 PO202610300003、配送单 DL202610300003。原生订单确认成功；提交合法的批号、日期和本次数量保存配送时，PUT /api/delivery-orders/3/edit 返回 HTTP 403、消息“无操作权限”。没有继续伪造出库或签收，也没有恢复已被移除的权限。

直接原因：控制器要求 deliveryOrders:pick，而 [V18__remove_logistics_permissions.sql](D:/SPD-MCP-UI/server/src/main/resources/db/migration/V18__remove_logistics_permissions.sql:1) 已从权限及角色关系中移除 deliveryOrders:* 和 shipmentOrders:*。测试管理员实际只有 purchaseOrders:confirm、purchaseOrders:createDelivery 等权限。合法请求重复执行仍为 403。

此外，即使解决此权限阻断，仍缺少以下接续关系：

- 医院上传计划、订单在第三期对接区，没有关联到可操作的供货单据；页面仅有明细查询。
- 当前 MCP 出站通信为目录/证照下发及结算协同反馈，原生配送/发货状态更新只写本地表，没有供货回传消息。
- SPD /api/mcp/receive 处理商品及证照载荷，没有接收供货单并创建关联收货单的契约。

供货尝试前后 SPD 收货单总数均为 0。本结论是“供货被阻断、回传能力未接通”，不是“已完成供货但回执偶发丢失”。后续开发应明确基于来源订单的供应商确认、批次和数量录入、稳定来源关联、持久回传消息以及 SPD 收货映射。

## 三、结算头与明细上传

为了独立测试结算上传，在 SPD 使用其正常采购收货流程对 CG20261030001 收货 3 支，收货单 RK20261030001，仓库 WH-CENTER，批号 ROUND-20261030；验收通过后自动产生 JS2026103000001。

**这张 SPD 收货单是结算测试的明确前置操作，不代表 MCP 供货回传成功。**

逐字段核对结果：

| 字段 | SPD | MCP |
| --- | --- | --- |
| 结算单号 | JS2026103000001 | JS2026103000001 |
| 来源商品 | ROUND_41819101 | ROUND_41819101 |
| 结算明细 ID | 1 | sourceLineId="1" |
| 结算数量 | 3 | "3" |
| 单价 | 1.2345 | "1.2345" |
| 明细金额 | 3.7035 | "3.7035" |
| 头金额 | 3.7035 | "3.7035" |
| 来源状态 | pending_confirm | pending_confirm |

MCP 同时保留 supplierCode=SUP-001、currency=CNY、taxIncluded=true、settlementMode=purchase_in、来源 receiving_order_item/1 及系统批次 PC2026103000001。页面实际显示四位金额。使用 Decimal 对两端数量、单价、行金额及头合计进行了断言，全部通过。

## 报告与截图

- [本次三条链路结构化结果（overall=partial）](D:/SPD-PR/output/playwright/plan-settlement-roundtrip/report.json)
- [采购计划接收页面](D:/SPD-PR/output/playwright/plan-settlement-roundtrip/mcp-plan-received.png)
- [计划 12 支明细](D:/SPD-PR/output/playwright/plan-settlement-roundtrip/mcp-plan-detail.png)
- [结算单接收页面](D:/SPD-PR/output/playwright/plan-settlement-roundtrip/mcp-settlement-received.png)
- [结算明细与四位金额](D:/SPD-PR/output/playwright/plan-settlement-roundtrip/mcp-settlement-detail.png)
- [供货保存 403 响应](D:/SPD-PR/output/playwright/plan-settlement-roundtrip/supply-permission-proof.txt)

既有双端自动联调本轮也重新执行，145 项全部通过：[自动联调报告](D:/SPD-MCP-UI/test-results/dual-system-20261030-142656/report.json)。该套件验证既有只读上传、版本、供应商隔离、取消及 1001 行分片，不包含尚未开发的供货回传闭环，因此不能用其全部通过替代本次供货失败结论。

页面测试运行目录 D:/SPD-MCP-UI/test-results/dual-system-20261030-142831，测试完成后关闭临时服务及浏览器。
