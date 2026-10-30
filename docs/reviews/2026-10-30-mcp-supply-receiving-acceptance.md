# MCP 供货与 SPD 收货闭环实施及验收

后续部署：已按用户授权完成本地业务环境 V106 / V29 迁移和供货开关启用，详见 [本地部署记录](2026-10-30-local-supply-deployment.md)。下文“尚未部署”为独立验收阶段的历史状态。

日期：2026-10-30。开发基于 SPD `f76c323d`，两端修改尚未提交。方案见 [供货接收方案](../plans/2026-10-30-mcp-supply-receiving.md)。

## 已实现行为

MCP 在“采购对接 → 医院计划与结算 → 医院订单供货”中，按医院上传的采购订单填写供货单号、院内库房、数量、批号、生产日期和有效期。实时读取 SPD 剩余可收数量；支持同一采购明细分批次供货。供应商权限按租户、实例和明确的供应商绑定校验，管理员提交也需要绑定。

提交进入持久化回传队列，冻结原始消息和目标地址。SPD 校验共享密钥、机构、供应商、采购明细、商品、单位、日期及剩余数量，只生成独立草稿收货单。院方在既有“供应链业务 → 收货验收”中确认合格/不合格数量并审核，只有合格数量进入库存。MCP 可刷新院方收货状态，按单位展示实际验收数量，也可分页查询原供货申报批次。

消息重试保持原内容和幂等标识，不重复建单或入库。网络失败可重试；业务拒绝可更正后重新提交。待收货草稿计入占用量，并发供货不能突破采购数量。院方不能通过修改导入收货单更换采购来源或增加未申报数量。未恢复旧原生配送菜单或权限。

## 自动验证

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| SPD 新增真实 MySQL 专项 | 13 通过 | `output/phase3-review-tests/phase3-review-20261030-163854/tests.log` |
| 双端供货专项 | 49 项检查通过 | `D:/SPD-MCP-UI/test-results/dual-system-20261030-162946/supply-roundtrip-report.json` |
| 原双端目录、计划、结算等回归 | 145 项检查通过 | `D:/SPD-MCP-UI/test-results/dual-system-20261030-163115/report.json` |
| SPD 后端 package | 成功，903 项中 165 项条件跳过，无失败 | `.tmp/supply-spd-backend.log` |
| MCP 后端 test/package | 59 通过 | `.tmp/supply-mcp-backend.log` |
| MCP 前端测试 | 30 通过 | `.tmp/supply-mcp-frontend-tests.log` |
| 两端前端构建、MCP 类型检查 | 成功 | `.tmp/supply-spd-frontend.log`、`.tmp/supply-mcp-build.log`、`.tmp/supply-mcp-type.log` |

SPD package 排除了既有较慢的 `PendingProductApplicationControllerTest`；165 项条件跳过不计作通过。新增供货数据库专项单独启用并全部执行。专项包含事务回滚、重复消息、并发超量、不同商品并发锁序、旧草稿预留、院方调整实收/质检数量、混合单位回执及 UDI 长度校验。

## 浏览器验收

使用 Playwright 操作独立测试环境：MCP UI 5175 / API 18080、SPD UI 1825 / API 11818、独立 MySQL 13326。未操作本地 ISPD 业务库。

真实目录下发及院内准入后，采购计划 `JH202610300002` 生成采购订单 `CG20261030001`，采购数量 12 支。MCP 页面提交供货单 `GH-UI-20261030`，数量 3 支、批号 `LOT-UI-20261030`，SPD 待收货列表生成 `RK20261030001`，显示草稿与 MCP 来源标识。院方通过“审核入库 → 确认审核”处理后，MCP 点击“刷新SPD状态”，显示“SPD已验收入库”，实收 3、合格 3、不合格 0，原批号和日期可查询。

截图及页面快照在 `output/playwright/supply-closed-loop/`：

- `spd-pending.png`：独立待收货单及来源。
- `spd-accepted.png`：院方已验收。
- `mcp-accepted.png`：最终验收回执及原始批次明细。
- `mcp-draft.txt` / `mcp-accepted.txt`：刷新前后回执。

浏览器复核发现新增弹窗被默认桌面宽度限制，已修正为桌面宽弹窗；1280px 视口下弹窗宽 1152px，最终截图确认信息可读，前端重新构建成功。浏览器中此前测试登录过期产生的 401 已重新登录解决，最终操作成功。

## 部署条件

1. SPD 应用 Flyway `V106__mcp_supply_receiving.sql`；MCP 应用 `V29__hospital_order_supply.sql`，前置迁移按正常版本链执行。
2. SPD 配置 `SPD_MCP_SUPPLY_ENABLED=true`，并保留机构、共享密钥等既有对接配置。
3. MCP 配置 `APP_PHASE3_ENABLED=true`、`APP_PHASE3_SUPPLY_ENABLED=true`，为操作员赋予 `integration:supply` 及必要读取权限，并在实例中明确绑定供应商。
4. 更新并重启两端服务及前端后验证医院实例地址与认证配置。

新增开关默认关闭。本次只在独立测试库执行 V106 / V29，尚未将两项迁移应用到本地业务库，也未替换常用 1820 / 1818 服务。
