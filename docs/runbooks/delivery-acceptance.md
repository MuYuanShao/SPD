# 配送并发与 UI 闭环验收

验收日期：2026-09-07。范围为本次配送修复方案中的散货、定数包拣配与签收、库存原子性、稳定追溯及相关 UI；不是全项目上线认证。

## 结果

| 验证 | 结果 |
| --- | --- |
| 完整默认 Maven 测试 | 624 项，0 失败、0 错误、14 项 opt-in 跳过 |
| 独立 MySQL 验收 | 12 项，0 失败、0 错误、0 跳过 |
| 真实 API Playwright | 7 项通过；最后布局复跑再次全部通过 |
| Flyway 空库迁移 | 73 个现存迁移全部成功执行至 V78 |
| 前端生产构建 | 通过 |
| JaCoCo | HTML/XML/CSV 已生成于 backend/target/site/jacoco/ |
| 代码检查 | git diff --check 通过；frontend/src 无生成 JS |
| 隔离库清理 | 最后只读核对 spd_test_ 加 32 位随机标识的数据库残留为 0 |

12 项独立验收由 DeliveryMysqlAcceptanceTest 的 8 项数据库用例和 1 项浏览器驱动用例，以及 OperationalHighValueMysqlConcurrencyTest 的 3 项用例组成。浏览器驱动用例实际执行下面的 7 项 Playwright 测试，不能将驱动用例数与浏览器数重复合并统计。

## 业务覆盖

| 行为 | 验收证据 |
| --- | --- |
| 库存 10 拣配 4，并发及重复签收 | 来源保持 6；保存的目标库增加 4；同科室另一库保持 0；只有一条签收入库事件 |
| 来源库存恰好等于拣配量 | 来源扣至 0 后仍可签收，不再次扣来源库存 |
| 同一申领并发拣配 | 两个请求仅一个成功；一张配送单；总量不超过申领量 |
| 目标库停用 | 拒绝签收；不替换同科室其他库；零入库，单据仍待签收 |
| 第二批次入库失败 | 通过隔离夹具的数量上限触发数据库异常；前一批次入库、事件和签收状态全部回滚 |
| 两个历史模板版本标签及 MULTI 旧编码 | 实际绑定标签成功签收；正确目标库存与稳定追溯关系；并发只生成两条签收追溯事件 |
| 一个标签缺少来源批次 | 整单拒绝；零入库、零签收事件，不能只签收另一包 |
| 模板版本不匹配 | 拣配拒绝，零配送单，标签保持可用 |
| 事件写入失败 | 库存余额回滚 |
| 并发计费回调 | 只生成一次扣减 |
| 原维护变量设置为 1 | 同一物理连接下，库存事件及追溯关系的 UPDATE/DELETE 均被四个触发器拒绝 |

科室夹具刻意使用相同显示名称，并为每个科室建立 A/B 两个目标库；断言按保存的稳定 ID 验证，不按名称猜测。

## UI 覆盖

- 1440×900、1280×720、390×900 分别执行散货和双包拣配、签收，共 6 项。
- 从真实登录接口获取 token 和用户信息；所有 /api/ 请求转发到随机端口的真实 Spring 服务，不使用假业务响应。
- 选择已审批申领，加载对应货源，提交真实拣配请求，再在列表完成签收。
- 散货不显示定数包候选，定数包不显示散货候选，与后端申领快照约束一致。
- 暂缓真实拣配请求的转发期间检查按钮禁用；重复派发点击后确认只发出一次请求，数据库也只生成一张单。
- 定数包详情在三种视口均打开，展示来源批次，焦点位于对话框内时 ESC 可关闭。
- 无效目标库场景显示后端具体原因、保留原单及可重试按钮，共 1 项。
- 截图使用实际视口并结束过渡动画；宽表次要字段保留容器内横向滚动。配送工作区改为上下布局，固定待拣配单号、操作及散货数量列。

证据位于：

- output/playwright/delivery-report/index.html
- output/playwright/delivery-browser.log
- output/playwright/delivery-{loose,quota_package}-{1440,1280,390}-{picking,signed}.png
- output/playwright/delivery-detail-{1440,1280,390}.png
- output/playwright/delivery-error.png
- backend/target/surefire-reports/

## 本轮验收驱动的修复

1. 不再因包标签已标记 signed 而跳过首次签收追溯同步；幂等判断改为该配送单的签收追溯事件。
2. 包签收前完整预检所有绑定、来源批次、数量和状态，阻断部分包缺失来源却整单成功的问题。
3. 配送动作捕获并展示业务错误，提交中锁定拣配和签收，完成后允许重试。
4. 移除与服务端模式约束冲突的混合拣配提示及不适用候选区。
5. 定数包详情复用 Element Plus 对话框，补齐 ESC、焦点约束和窄屏内容滚动。
6. 修正配送工作区双栏挤压主表的问题；没有改写用户新增的全局样式或其他页面。

## 安全复跑

先通过安全方式设置以下环境变量，值不要写入仓库：

- SPD_TEST_MYSQL_URL：仅服务器 URL，例如 jdbc:mysql://localhost:3306/，不能包含业务库名。
- SPD_TEST_MYSQL_USERNAME、SPD_TEST_MYSQL_PASSWORD：具有创建、销毁独立测试库以及迁移触发器所需权限的账号。

单独启动本机前端：

```powershell
npm --prefix frontend run dev -- --host 127.0.0.1
```

然后在另一终端运行：

```powershell
$env:SPD_MYSQL_INTEGRATION_TESTS='true'
$env:SPD_DELIVERY_UI_TESTS='true'
Push-Location backend
.\mvnw.cmd "-Dtest=DeliveryMysqlAcceptanceTest,OperationalHighValueMysqlConcurrencyTest" test
Pop-Location
```

隔离后端只绑定 127.0.0.1 的随机端口；每次创建唯一测试库，执行真实 Flyway，并在结束时销毁该库。浏览器仅获得一次性测试登录凭证，不继承数据库凭证。测试不会启动业务后端、修改数据库全局权限、修改历史事件或执行业务库存补偿。

## 边界

- V78 仅在隔离库验证；本轮未迁移 ISPD，未部署、打包或提交 Git。
- 历史异常清单脚本保留在 docs/runbooks/sql/delivery-ledger-review.sql；本轮没有对业务库执行修复或补偿。
- 前端构建仍提示已有的 vendor 循环分块及大分块警告，未影响本轮构建和验收通过。
- 当前验收不替代全系统权限矩阵、全部浏览器/缩放组合或生产压力测试。
