# 第三期代码审计修复与回归

基准：对提交 `90bc9302` 的 Standards / Spec 审计。三项功能缺陷及一项维护性建议均已处理。

## 修复

1. **重新发布保留财务口径。** 优先复制原冻结载荷；最新业务版本未发送时，从该来源已有冻结快照保留币种、税口径，同时保留最新业务内容。领取阶段不会用当前环境配置覆盖已保留的口径。新消息仍使用新的消息 ID、revision 和摘要。
2. **目标切换后的租约恢复。** 有效发送租约期间拒绝重新发布，业务版本不推进。租约过期后，显式重新发布将旧发送记录设为 superseded 并清除租约，解除旧目标的顺序阻塞；迟到回执不能凭旧 lease_id 更新记录。没有显式重发时仍禁止新目标领取旧目标消息。
3. **回填强制范围。** 后端要求唯一供应商编码与起止日期，计划按 create_time、结算按 generate_time 筛选。开始日含零点、结束日含当天全部时刻，使用结束次日零点的半开区间。重复及限量回填始终使用原范围。页面添加必填字段、日期约束、提交确认和范围提示。
4. **领取结果明确类型。** 使用 sealed interface、枚举和 record 表达无消息、配置阻塞及已领取消息，消除异构 Map 的隐式状态协议。

无需新增 Flyway 迁移，未修改生产数据库，未提交 Git commit。未增加采购审批、结算确认或付款行为。

## 可复现回归

运行 `python scripts/test-mcp-phase3-review.py`，脚本在 13327 端口启动一次性 MySQL，应用真实 V104 迁移并运行 `McpPhase3ReviewMysqlTest`，结束后关闭测试实例。生成的数据及日志位于忽略目录 output/phase3-review-tests。

修复前最小回归确实复现：原 CNY 被重发为 USD；目标切换后的新版本发送数为零；无范围回填没有抛出校验错误。修复后 **10 项真实 MySQL 回归全部通过**，覆盖上述场景以及有效租约保护、旧租约回执隔离、未发送新版本、连续重发、不同供应商、日期端点、草稿排除、分批重复和非法范围拒绝。

最新回归日志：[tests.log](D:/SPD-PR/output/phase3-review-tests/phase3-review-20261030-102325/tests.log)。

## 综合验证

- 后端广泛回归：881 项，0 失败、0 错误、143 跳过。143 项包括原有 133 项可选测试和本轮新增 10 项临时数据库测试；新增测试已由专属脚本单独通过。与前轮一致，排除运行耗时较长、此前单独通过的 PendingProductApplicationControllerTest。
- 前端 `npm --prefix frontend run build` 通过，保留既有包体积警告。
- 修复后的双端隔离联调 **145 项全部通过**，含 1001 行分片、来源金额、供应商隔离、取消及重发。报告：[report.json](D:/SPD-MCP-UI/test-results/dual-system-20261030-102112/report.json)。
- Chrome 在专属 1825 页面端口通过真实登录，验证必填范围、包含供应商和两端日期的确认提示，以及实际 POST 参数。有效请求返回 code=0，旧版无范围请求被后端返回业务错误 code=400（沿用现有 HTTP 200 的 ApiResponse 约定）。
- 页面验收截图：[回填范围](D:/SPD-PR/output/playwright/phase3-review-backfill.png)。

验证使用自建数据库和专属端口，没有连接业务数据库或更改业务服务。正式环境原有仅传 size 的回填调用需更新为包含 supplierCode、startDate、endDate，详见 [实施说明](../plans/2026-10-30-mcp-phase3-implementation.md)。
