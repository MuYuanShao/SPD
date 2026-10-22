# 医院目录交互与提交验证

日期：2026-10-22。

## 行为

医院目录列表和商品详情使用同一个模块标签。列表修改与详情编辑复用 ProductDialog；历史新增、编辑和批量编辑 URL 重定向至目录中的现有弹窗，避免两套编辑入口产生不同体验。独立旧表单文件不再由路由加载。

详情按当前商品重新读取并丢弃过期响应，返回按钮明确回到医院目录。列表返回保留查询、页码、列配置与横纵滚动位置。

编辑和批量编辑增加未保存离开确认、保存防重、错误反馈及提交期间输入/关闭/导航保护。批量操作在打开时保存所选商品，避免列表异步响应清空提交对象。

保存仍创建审批申请，不直接变更正式目录。成功后保留目录页面，提示最终审批通过后生效；单笔保存提供主动查看审批单入口。审批队列和待办通过现有失效版本刷新，最终审批通过后刷新正式目录。

## 验证

- 医院目录 10 项浏览器场景通过：同标签、A/B 详情切换、统一编辑、保存防重、脏修改取消关闭/取消关闭标签、失败重试、旧新增/编辑/批量地址、直达详情返回、滚动恢复和延迟响应防串单。运行记录：`output/hospital-catalog-navigation-smoke.log`。
- 既有审批回归 20/20 通过：`output/hospital-catalog-approval-regression.log`。
- 前端类型检查与构建通过：`output/hospital-catalog-navigation-build.log`。
- 本次提交还包含此前已完成的工作台中文文案、系统操作记录独立入口、基础数据及定数包菜单调整、审批交互和侧栏紧凑布局。相关后端 RbacAuthorizationServiceTest、DashboardServiceTest、AuditLogQueryServiceTest 共 19 项通过，失败和错误均为 0。运行记录：`output/catalog-release-backend-tests-elevated.log`。
- 文本编码、Vue 单文件结构和差异空白检查通过。

浏览器场景全部拦截业务 API，未修改真实审批与目录数据；后端定向测试使用模拟依赖。浏览器验证确认前端交互，真实审批事务和跨用户推送不属于该验证结论。

本机系统 Maven 版本低于仓库要求，实际后端测试使用 backend/mvnw.cmd；沙箱 Java 附加管道受限，重试在宿主环境成功完成，未改动测试配置。

## Git 范围

推送目标为已配置的 origin：`https://github.com/MuYuanShao/SPD.git`。本地 Git 提交后推送同一 GitHub 仓库的 main 分支，不强制覆盖远程历史。
