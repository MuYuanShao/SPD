# CLAUDE.md

本文件为 Claude 提供院内 SPD 仓库的工作指南。`AGENTS.md` 是适用于所有智能代理的项目指令，以其内容为准。修改代码前，请完整阅读并遵循 `AGENTS.md`。若两份文件存在差异，以 `AGENTS.md` 为准，并更新本文件以保持一致。

## 必读项目资料

变更功能行为前，请阅读以下资料。

- `AGENTS.md`
- `CONTEXT.md`
- `docs/code-standards.md`
- `docs/ARCHITECTURE.md`
- 相关源码、Flyway 迁移及测试

本项目是一套用于院内日常业务的 SPD 供应链平台。

- 前端为 Vue 3、Vite、Element Plus 和 TypeScript。
- 后端为 Spring Boot 3、Java 17、JDBC 和 MySQL。
- 数据库结构统一通过 Flyway 管理。
- 本地前端地址为 `http://localhost:1820`。
- 本地 API 地址为 `http://localhost:1818/api`。
- 本地数据库为 MySQL `ISPD`。

## 仓库目录

| 内容 | 路径 |
|---|---|
| 后端代码 | `backend/src/main/java/com/hospital/spd` |
| 后端测试 | `backend/src/test/java/com/hospital/spd` |
| 数据库迁移 | `backend/src/main/resources/db/migration` |
| 前端页面 | `frontend/src/views` |
| 前端 API 客户端 | `frontend/src/api` |
| 路由 | `frontend/src/router/index.ts` |
| 功能目录 | `frontend/src/config/featureCatalog.ts` |
| 共享组件 | `frontend/src/components/common` |
| 全局样式 | `frontend/src/styles/main.css` |
| 浏览器冒烟测试 | `tests/e2e` |

## 领域约束

- 库存余额（Inventory Balance）表示当前库房、货位、商品和批次对应的库存数量，始终必须大于或等于零。
- 库存流转（Inventory Movement）负责入库、先进先出扣减、库存调整、调拨、隔离、加锁和写入不可变的库存事件（Inventory Event）。
- 控制器负责绑定输入并返回 `ApiResponse`。业务校验、状态转换、库存写入、审计写入和单据编号生成由服务层负责。
- 通用业务行为优先复用 `DocumentNumberService`、`AuditLogService`、`InventoryEventService` 和 `OperatorContextProvider`。
- 分页接口返回 `rows`、`total`、`page`、`size`，并可返回 `summary`。
- 数据库结构变更通过新增 Flyway 迁移实现，禁止依赖手工修改数据库。

## 必须遵守的产品边界

- 「库存管理」用于查询库存余额。库存交易流水作为独立功能提供。
- 禁止新增或恢复「库存事件快照」业务模块，也禁止在补货、消耗、收费明细、冷链、召回或其他业务页面展示其功能或数据。
- 原「高值耗材管理」页面定位为「收费耗材明细查询」。支持按患者、商品、日期、住院号、UDI／唯一码、供应商、厂家、注册证和目录字段查询。结果展示医院目录信息、收费数量、单价、金额和患者数据。
- 科室领出和高值计费是独立环节。SPD 必须收到并校验来自 HIS、麻醉或手术系统的计费数据后，才能扣减高值耗材库存。
- UDI 追溯同时覆盖高值耗材和低值定数包。
- 持久化 UDI 关系时，用 `trace_code_id` 作为稳定的关联标识。UDI 和唯一码文本用于接口传输、搜索及展示。
- 进入「科室申领」时先展示历史申领单汇总。新建申领单时，仅使用当前科室或库房的目录，禁止使用全院目录。
- 业务页面应紧凑、便于快速浏览。只有在用户明确要求时，才添加大型顶部概览或统计卡片。

## 常用命令

```powershell
# 前端
npm run dev:frontend
npm --prefix frontend run build

# 后端
mvn -f backend/pom.xml test
mvn -f backend/pom.xml spring-boot:run

# 本地后端管理
npm run backend:start
npm run backend:restart
npm run backend:status
npm run backend:stop

# 一键启动与离线部署打包
npm run start:dev           # 一键启动前后端（检查 MySQL → 后端 → 前端）
npm run package:offline     # 打包后端 jar + 前端构建产物 + JDK17 + 一键脚本到 output/offline-bundle

# 发布与浏览器验证
npm run verify:release
npm run test:smoke:business-ui
```

运行后端专项测试。

```powershell
mvn -f backend/pom.xml "-Dtest=TargetTestClass" test
```

真实 MySQL 计费并发测试会创建并清理测试数据，因此必须显式启用。

```powershell
$env:SPD_MYSQL_INTEGRATION_TESTS='true'
mvn -f backend/pom.xml "-Dtest=OperationalHighValueMysqlConcurrencyTest" test
```

常规单元测试不得隐式启用会修改真实数据库的用例。

## 验证要求

- 修改后端后，先运行专项测试。条件允许时，再运行完整后端测试套件。
- 修改前端后，运行生产构建。
- 修改关键业务页面交互后，在前端、后端及 MySQL 可用的环境中运行 `npm run test:smoke:business-ui`。
- 修改前后端集成时，核对 `/api` 路径、认证、真实数据库持久化以及实际使用的前端 API 客户端。
- 修改库存或单据编号逻辑时，按改动风险提供相应的并发验证结果。
- 前端构建完成后，确认 `frontend/src` 下的 TypeScript 或 Vue 源文件旁没有生成同名的 `*.js` 或 `*.vue.js` 文件。

## 编辑与工作区安全

- 工作区有未提交改动时，保留与当前任务无关的用户修改。
- 将修改限定在当前任务范围内，避免顺手重构。
- 禁止重置或覆盖无关文件。
- 源码、SQL 和文档使用 UTF-8 编码。PowerShell 输出乱码本身不足以证明文件已损坏。
- 界面文案和 API 消息使用可读的中文。
- 用户常通过 `localhost:1820` 验证。修改运行中的功能后，按需重新构建或重启相关服务。

## 同步项目指令

项目命令、目录结构、领域边界或验证要求发生变化时，按以下顺序更新。

1. 先更新 `AGENTS.md`。
2. 更新本文件中受影响的对应说明。
3. 对经常变化的细节，优先引用 `AGENTS.md`，减少重复维护。
4. 完成前核对两份文件的产品边界是否一致。

## 智能代理技能

### 问题跟踪

问题和产品需求文档（PRD）在 `MuYuanShao/SPD` 的 GitHub Issues 中跟踪。详见 `docs/agents/issue-tracker.md`。

### 分诊标签

使用默认的五角色分诊术语。详见 `docs/agents/triage-labels.md`。

### 领域文档

本仓库采用单一领域上下文，使用根目录的 `CONTEXT.md` 与 `docs/adr/`。详见 `docs/agents/domain.md`。
