# 首页工作台真实数据对接

验收日期：2026-09-11。

## 范围

首页继续使用原有 StatisticCard、AlertCard、TodoList、NoticeList、TrendChart、表格和弹窗。未修改组件源码、布局或样式；仅替换首页数据适配、绑定、查询和反馈。旧 GET /api/dashboard 保留，实时工作台不受接口破坏性变更影响。

新增只读接口：

- GET /api/dashboard/workbench：当日指标、30 天趋势、预警、授权范围内待办及数据口径。
- GET /api/dashboard/products?page=1&size=5&keyword=：库存余额记录的服务器分页。

沿用首页读取权限和 DataScopeService。所有 SQL 参数化；单头/余额查询不通过名称展开造成重复。高值计费历史表只有科室名称，受限用户仅在名称唯一匹配时可见；歧义名称不猜测归属。

## 统计口径

| 展示 | 来源与规则 |
| --- | --- |
| 今日销售额、售出数量 | confirmed 科室消耗明细＋charged 高值计费；排除未确认、已冲销状态；related_biz_type=high_value_charge 的科室镜像消耗不重复计算 |
| 今日入库、出库 | inventory_event 当天正/负数量，出库取绝对值；零数量调价事件不计入数量 |
| 近30天出入库金额 | 不可变 amount_snapshot，出库金额转为正值；不读取变更后的批次当前价重算历史 |
| 耗用金额 | 同销售口径，元转万元展示 |
| 采购金额 | 按审批日期统计 approved/sent/closed 采购订单总额；不含草稿、未审批和作废订单 |
| 库存下限、缺货 | 启用且未删除的科室安全量规则，对比该科室有效库房的可用库存；下限不足或可用量为零 |
| 效期预警 | 有可用库存的余额记录，其批次有效期在阈值内或已过期 |
| 不良品待处理 | 有隔离数量的库存余额记录数 |
| 证照预警 | 可见库存相关的有效证照，在阈值内或已过期；按证照记录计数，不按关联库存重复展开 |
| 滞销提醒 | 批次建立超过90天，当前有可用库存，近90天该库房/商品/批次没有出库事件 |

30 天包含数据库当前日期，缺少业务的日期补零。效期天数读取有效的全局 warning.stock.threshold.expiryWarningDays；未配置默认为30，非法或冲突配置明确报错。滞销口径为90天，响应 definitions 和顶部状态标签提示中说明。

待办展示可读权限与数据域内的采购订单待审、收货草稿、待审/待配送申领。首页预览最多8条，单独返回完整 total。不绕过对应业务模块的审批权限。暂无统一权威进度字段，待办均以未完成的0值传入既有进度组件，不伪造35%等阶段百分比。

## 明确缺失的数据

- 未找到公告存储/发布源：返回空 notices 和 noticeSourceAvailable=false，使用既有空状态；不使用审计日志冒充公告。
- 未找到注册证持有人字段：库存列表 holder 返回“—”含义的占位“-”，不将厂家或供应商冒充持有人。名称、规格、库房/货位及编码可检索；不存在的持有人数据无法检索。
- 常用功能继续依据真实登录权限生成；本轮未新增公告管理、主数据字段或个人偏好存储业务。

## 健壮性

- 搜索防抖并回到第一页，筛选/分页在服务器执行；特殊 LIKE 字符按字面匹配，超长关键词和溢出页码拒绝。
- 使用 AbortController 和请求序号，旧请求、已卸载页面的响应不能覆盖新状态。
- 可见页面每60秒重新读取；顶部原有标签可点击或按回车重试。
- 错误时清除失效数据，指标显示占位，不退回演示数据；汇总与库存列表分别处理失败。
- 对响应关键字段、数量和金额执行运行时校验；图表按万元换算，不改展示组件。

## 验证

- 完整 Maven：631 项，0 失败、0 错误；19 项 opt-in 默认跳过。
- 首页聚焦单测：2 项通过，原 DashboardServiceTest 继续通过。
- 隔离 MySQL：5 项全部通过，覆盖确认/冲销/镜像排除、历史事件金额、分页/筛选、科室隔离、同名科室歧义、有效预警配置及软删除规则。
- 真实浏览器脚本通过：汇总数值、四个原统计组件、公告空状态、服务器搜索/分页、空列表、服务失败/重试、慢旧搜索取消。
- 前端生产构建通过；仅有现有大分块体积警告。git diff --check 通过；frontend/src 无新增生成 JS。
- 本地前端代理实际读取 /api/dashboard/workbench 返回 code=0、30天；/api/dashboard/products 返回 code=0、第一页5条（当时共42条）。业务库迁移版本仍是78。

证据：output/playwright/home-smoke.log、home-workbench-real.png、home-workbench-snapshot.txt；后端报告位于 backend/target/surefire-reports 和 backend/target/site/jacoco。

## 复跑

启动已有前端；通过环境变量安全提供 SPD_TEST_MYSQL_URL（仅服务器）、SPD_TEST_MYSQL_USERNAME、SPD_TEST_MYSQL_PASSWORD。不要将密码写入文件。

```powershell
$env:SPD_MYSQL_INTEGRATION_TESTS='true'
$env:SPD_HOME_UI_TESTS='true'
Push-Location backend
.\mvnw.cmd "-Dtest=DashboardWorkbenchMysqlTest,DashboardWorkbenchServiceTest,DashboardServiceTest" test
Pop-Location
```

测试后端仅监听本机随机端口，创建唯一隔离库并在结束时销毁；浏览器不继承数据库凭证。模拟失败仅用于验证错误路径，正常业务请求全部转发到真实测试后端。没有修改业务数据、新增数据库结构或提交 Git。
