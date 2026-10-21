# 2026-10-21 审查问题修复记录

审查范围为当前工作区，包含未提交改动；版本保持 `20261020`。原始报告位于 `output/reviews/2026-10-20-full-worktree-review.md`。

## 修复结果

| 编号 | 问题 | 修复与验证 |
| --- | --- | --- |
| S1 / P1 | 定数包 UDI 误走普通消耗 | 按稳定 trace_code_id 关联定数包，标签号、UDI、唯一码返回同一 package 身份，页面进入原有整包消耗状态机。单元测试及真实 MySQL 三种码查询通过。 |
| S2 / P1 | 结算确认与反消耗竞态 | 反消耗先锁定全部关联结算单，再判断确认状态；待确认条目的移除复用已锁定结果。真实双事务测试证明：结算持锁时反消耗等待，确认提交后反消耗拒绝，库存恢复方法未被调用。 |
| S3 / P2 | 未完整盘点也能复核 | 在任何库存调整前校验全部明细都有合法实盘数量及差异。部分明细缺失时整单拒绝；测试断言未调用库存调整。 |
| R1 / P1 | 升级删除自身应用源文件 | 停服务之前校验源包完整性和目录关系，拒绝同目录、大小写变体及互相嵌套。路径保护测试确认拒绝时源文件仍存在。升级须从新解压且独立的完整包启动。 |
| R2 / P2 | 审批“本部门及下级”仅允许本部门 | 按部门祖先链判断任意层级，检测循环并拒绝未知范围。祖孙部门通过、无关部门拒绝的回归用例通过。 |
| R3 / P2 | 审计日志筛选字段不一致 | 后端明确返回 operationType/bizType，前端分开定义操作类型和业务类型选项。真实 API 和 Chrome 测试验证选项、查询参数、数据库结果及页面渲染。 |
| R4 / P2 | 采购动作权限与入口鉴权冲突 | 入口按采购订单、需求、计划及操作路径检查细粒度权限；共享 action URL 允许对应动作权限进入，服务仍校验请求体中的确切动作。创建权限不再依赖额外 broad write，且不能进入审批或附件接口。 |
| R5 / P2 | DDL 检查遗漏角色继承 | 激活可用角色后读取 ENABLED_ROLES，使用 SHOW GRANTS ... USING 展开有效权限，并安全转义角色名称。独立 MySQL 验证嵌套角色 DDL 被拒绝，纯 DML 角色被接受。 |

浏览器验收额外发现并修复了离线 SPA 历史路由无限转发：由资源解析器处理历史路由回退，index.html 和静态资源不再被 Controller 重复转发。测试覆盖入口、深层路由、脚本内容和缺失资源 404；真实 Chrome 登录与审计页面操作通过。

## 验证结果

- 修复前，新增回归用例出现 4 个失败及 1 个错误，复现了消耗识别、盘点、审批范围和采购权限问题。
- 最终完整后端 `mvnw.cmd clean package`：724 项，0 失败、0 错误、45 项默认跳过。
- 显式独立 MySQL 回归 `ReviewRegressionMysqlTest`：2 项通过，不启用现有业务数据库并发测试。
- 内置 MySQL 综合验收：17 项检查全部通过，包含真实 Chrome 审计筛选。
- 前端生产构建、生产依赖漏洞审计、UTF-8、Flyway 历史哈希和 PowerShell 语法检查通过。
- 安装路径保护测试通过；离线包结构及逐文件 SHA-256 检查通过。

证据：

- `output/review-fixes-red.log`
- `output/review-fixes-backend.log`
- `output/review-fixes-frontend.log`
- `output/review-fixes-spa.log`
- `output/review-fixes-acceptance.log`
- `output/embedded-mysql-acceptance/b881f3857e1340c8913b0f9ee2a8dc98/acceptance.json`
- `output/review-fixes-package-final.log`

## 运行边界

本次使用独立临时 MySQL、随机端口和自动删除的专用回归 schema，未修复或迁移现有业务库。当前本地 1818 服务不可访问；此前完整业务 UI 冒烟存在旧库 Flyway 冲突，因此本轮对受影响页面采用独立生产配置应用与真实数据库进行浏览器验证。Windows 服务注册、开机自启及正式服务器安装仍需在目标机验收。

修复前的离线 ZIP 已保存在 `output/backups/pre-review-fixes-20261021/`。最终交付 ZIP 保持 `spd-server-offline-20261020.zip` 名称；以同目录新生成的 SHA-256 文件为准。代码尚未提交 Git。
