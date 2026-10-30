# 混合采购计划自动拆单修复与验收

日期：2026-10-31。按 [分析方案](../plans/2026-10-31-mixed-purchase-plan-analysis.md) 完成代码修改、真实数据库及双端验证，并更新本地业务环境。修改尚未提交。

## 最终业务行为

- 同一次选择的已审核需求生成一张计划，支持混合商品、配送商和高低值。
- 计划明细保存配送商、数量、单位和创建时估价快照。取消计划头统一供应商覆盖，不再缺省选取第一家供应商。
- 审批开始前可在“明细/配送商”中确认配送商；开始审批后禁止静默修改。
- 多级审批仅最终通过才自动拆单。按明细配送商分组，同一配送商的高低值进入同一张订单。
- 新订单承接计划审批授权，状态为approved，保存来源计划、来源明细、创建归属及审批人，不再要求人工重复点击转订单才能供货。
- 自动订单保留计划创建人的数据归属，审批人独立记录，避免最终审批人不同导致申请人丢失订单访问范围。
- 返回全部orderNos；重复审批或旧execute请求返回已有结果，不重复建单。某组失败回滚整个最终审批/建单事务。
- MCP每张供应商订单保存相同的真实来源计划ID。计划查询按供应商建立独立投影，仅包含本配送商明细，保留共同院内计划号。
- 修正MCP计划明细显示：单价使用estimatedUnitPrice，金额使用estimatedAmount，历史未记录估价保持明确缺失，不伪造零。
- 高值扫码占用、科室签收转库及计费扣库逻辑未作改变。

历史单商品计划回填一条明细及对应供应商投影；保留原计划号和原外部投影/明细身份，不合并或改号。历史已生成订单状态保持，旧的已审批未转单计划仍通过受控execute入口调用相同拆单服务，不在启动时自动批量补生成。

## 实现范围

SPD：

- `V107__mixed_purchase_plans.sql`：单头多明细、供应商投影、订单及订单明细的来源关系、唯一约束与历史回填。
- `MixedPurchasePlanService`：计划快照、数据范围、配送商编辑、分组生成订单及幂等。
- `PurchaseFlowCommandRunner`：保留审批流门槛，最终审批后自动执行。
- `McpPhase3Outbox`：按稳定供应商投影生成计划报文；历史回填仍限制指定供应商和时间范围。
- `McpUploadJob`：从订单直接读取来源计划，不依赖单值converted_order_no。
- 计划列表按单据显示明细数、配送商数及订单数；新增真实API驱动的计划明细弹窗。

MCP：

- `app/services/phase3.ts`、`app/components/Phase3Management.tsx`：补充计划估价单价字段，分别展示单价及金额。
- 补充实际金额与单价显示回归测试。接收协议已经支持供应商独立来源身份，不需要追加MCP数据库迁移。

## 验证结果

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| SPD后端完整测试 | 920项，无失败/错误，179项条件跳过，即741项实际执行通过 | `.tmp/mixed-plan-full-tests.log` |
| 新混合计划真实MySQL专项 | **14通过**，含V106→V107、并发防重、失败回滚、同配送商合单、快照、数据范围、创建人与审批人归属 | `output/phase3-review-tests/phase3-review-20261031-003002/tests.log` |
| 原计划结算Outbox真实MySQL回归 | **10通过** | `output/phase3-review-tests/phase3-review-20261031-001910/tests.log` |
| 原供货接收真实MySQL回归 | **13通过** | `output/phase3-review-tests/phase3-review-20261031-001930/tests.log` |
| 最终版本独立双端全链路 | **69检查通过** | `D:/SPD-MCP-UI/test-results/dual-system-20261031-003308/mixed-plan-report.json` |
| MCP前端测试 | **31通过** | `.tmp/mixed-plan-mcp-tests.log` |
| SPD/MCP前端构建、MCP类型检查、SPD打包 | 通过 | `.tmp/mixed-plan-frontend.log`、`.tmp/mixed-plan-mcp-build.log`、`.tmp/mixed-plan-mcp-type.log`、`.tmp/mixed-plan-package.log` |
| Flyway文件、RBAC与文本编码检查 | 通过 | 对应根目录verify脚本 |
| 固定业务页面冒烟 | **5通过、1失败** | `.tmp/mixed-plan-business-smoke.log` |

固定冒烟失败项是“AI医护助手的自定义补货问题返回补货建议入口”：响应业务code=400，服务日志为SocketTimeoutException，提示AI模型服务连接/响应超时。本次没有修改AI实现或模型服务配置，未将该项标为通过。收费耗材明细、待审批目录、拣配空状态、科室申领及收货交互5项通过。

独立双端使用自建MySQL和专用11818/18080端口，测试后停止并清理自建进程；不是通过修改业务库状态制造成功。测试脚本为 `scripts/test-mixed-purchase-plan-roundtrip.py`，覆盖：

1. MCP两项商品真实下发、院内准入及管理标志确认。
2. 混合计划最终审批直接产生两个配送商订单，无人工execute补转。
3. 两订单上传后来源计划ID相同，供应商及明细正确。
4. 各供应商计划投影独立、共用院内计划号，越权读取被拒绝。
5. 重复审批及兼容旧execute不重复建单。
6. 另一张混合计划修改明细配送商后，高低值合成一张订单。
7. 供货回传先产生待收货，院方审核后才入一级库。
8. 定数包及高值扫码拣配、占用、签收、重复签收、低值扫码消耗、幂等HIS模拟计费。
9. 两供应商结算生成、正确金额上传及隔离读取。

## 本地迁移及页面验收

已将本地ISPD从V106升至V107，Flyway迁移和校验通过，未执行repair。MCP数据库仍为V29。

迁移前完整备份：

`output/mixed-plan-deployment/ISPD-before-V107-20261031-002141.sql`

大小3038172字节，SHA-256：`8fb5fa5e3954f51006e004bd843e0dfe09fcc37c535a44899e3bc9d1d27195e5`。

迁移当时原记录数保持：计划14、订单31、订单明细32、库存余额65、收货40。回填计划明细14、供应商投影14。迁移不操作库存余额。备份摘要、迁移日志和核验在 `output/mixed-plan-deployment/`，包含业务数据的SQL未加入版本库。

随后在本地新增真实页面验收需求 `XQ202610310001`，生成唯一混合计划 **JH202610310001**：

| 商品 | 配送商 | 数量 | 快照单价 | 自动生成订单 |
| --- | --- | --- | --- | --- |
| MCPg | 测试A供应商 | 20支 | 2.5000 | CG20261031001 |
| MCPd | 测试B供应商 | 4支 | 100.0000 | CG20261031002 |

Playwright实际点击“计划审核”，页面直接提示生成两张订单，无第二次转单操作；再次打开明细，配送商已不可编辑。两张订单已上传MCP，`sourcePlanId` 均为15，`sourcePlanNo`均为JH202610310001；重复审批仍返回这两张订单。

分别登录本地供应商A/B，计划明细页同为JH202610310001：A仅见MCPg 20支，单价2.5000、金额50.0000；B仅见MCPd 4支，单价100.0000、金额400.0000。此处只有本地计划及订单验收，没有为这两张本地新订单继续新增供货/收货库存；完整下游操作在独立双端环境完成。

- [本地API验收记录](D:/SPD-PR/output/mixed-plan-deployment/local-acceptance.json)
- [审批前混合明细](D:/SPD-PR/output/playwright/mixed-plan-fix/before-approval.png)
- [页面最终审批后生成订单](D:/SPD-PR/output/playwright/mixed-plan-fix/after-approval.png)
- [MCP供应商A专属计划明细](D:/SPD-PR/output/playwright/mixed-plan-fix/mcp-a-plan.png)
- [MCP供应商B专属计划明细](D:/SPD-PR/output/playwright/mixed-plan-fix/mcp-b-plan.png)

SPD后端已使用最新jar重启，SPD 1820/1818、MCP 5173/8080均返回200。SPD运行日志及单独启动脚本在 `output/mixed-plan-deployment/`。本次没有提交代码或创建Windows自启动服务。
