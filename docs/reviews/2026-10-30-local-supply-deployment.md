# 本地业务环境供货功能部署记录

日期：2026-10-30。按用户授权部署 SPD / MCP 最新已验收构建，应用数据库迁移并启用供货开关。

## 迁移及备份

| 本地数据库 | 升级前 | 本次迁移 | 升级后 |
| --- | --- | --- | --- |
| MySQL 3306 / ISPD | V105 | V106 | V106 |
| MySQL 3306 / spd-mcp | V27 | V28、V29 | V29 |

迁移前分别创建包含表、过程、触发器及事件的完整备份，保存 SHA-256，并在迁移前重新核对摘要。Flyway 先预检（仅忽略待应用版本），再按明确目标版本迁移、完整校验；两端校验通过，失败迁移均为 0。未执行 repair 或修改历史校验和。

备份在 Git 忽略目录 `output/local-supply-deploy/`：

- `ISPD-before-supply-20261030-224915.sql`，2479379 字节，SHA-256 `527d7765d9389f67c355bcbb0998dc148bcc75b59baefb1b0d10ac367afe058f`。
- `spd-mcp-before-supply-20261030-224915.sql`，11529420 字节，SHA-256 `81f5332386936ba156acd5f752e2f9dbdf062d6342f6b009ab251804e05637c6`。
- `backup-manifest.json`、`after-migration.json` 及两库 `*-migrate.log`、`*-validate.log` 保存审计证据。

## 运行配置

在 `D:/SPD-MCP-UI/.env` 持久化以下开关，由本地启动脚本加载：

```text
APP_PHASE3_ENABLED=true
APP_PHASE3_SUPPLY_ENABLED=true
SPD_MCP_SUPPLY_ENABLED=true
```

本地启动脚本为 [start-local.ps1](D:/SPD-PR/output/local-supply-deploy/start-local.ps1)，使用已有私有 `.env` 配置，不在脚本中复制对接密钥。MCP 使用本地 `spd-mcp` 数据库，SPD 使用 ISPD 和 local 配置。SPD 数据源显式使用 SPD 连接配置，避免继承 MCP 的 Spring 数据源；SPD 前端工作目录为 `frontend`。

两端使用前次已通过测试的 jar 和当前前端源码运行。已核对部署 jar 内 V106 / V29 SQL 与源码一致。初次启动中的 SPD 数据源环境覆盖和前端工作目录问题已经修正，随后重启成功。

| 服务 | 地址 | 本次运行 PID |
| --- | --- | --- |
| SPD 前端 | http://localhost:1820 | 21692 |
| SPD 后端 | http://localhost:1818/api | 11560 |
| MCP 前端 | http://localhost:5173 | 22316 |
| MCP 后端 | http://localhost:8080/api | 15840 |

后台进程以隐藏窗口运行；日志和 PID 清单保存在 `output/local-supply-deploy/`。关闭这四个进程后可使用上述启动脚本再次启动；端口已占用时脚本会停止并要求先核对进程，不自动杀死其他服务。不是 Windows 开机自启动服务。

## 部署核验

17 项部署检查通过，结果见 [verification.json](D:/SPD-PR/output/local-supply-deploy/verification.json)：

- 两端后端健康接口与前端首页均返回 200。
- MCP 既有管理员可登录；供货能力接口返回 `enabled=true`，医院上传订单列表接口可读。
- SPD 共享密钥、服务操作员和供货权限生效；查询随机不存在回执返回预期业务错误，而不是禁用或权限错误。未认证访问返回 401。
- 新增收货来源字段、接收台账和供货队列存在；既有管理员供货权限已由迁移授予。
- 本地医院实例仍指向 `http://127.0.0.1:1818/api/mcp/receive`，机构为 `SPD_PR_LOCAL`。
- 两端迁移版本与部署包内 SQL 核对一致。

当前医院上传订单数为 0，供应商账号绑定数为 0。管理员也必须在实例的“供应商数据范围配置”中明确绑定供应商后，才能为该供应商提交供货。未自行扩大任何账号的供应商数据范围。

本次只做部署与只读供货核验，未创建测试采购订单、供货单或收货单，也未进行测试入库。服务启动时既有库存日报和驾驶舱任务正常运行，补齐了历史汇总；不应将“未创建测试单据”理解为启动后数据库完全没有正常业务任务写入。

功能闭环和并发、幂等验证沿用 [独立环境验收记录](2026-10-30-mcp-supply-receiving-acceptance.md)，未在业务库重复执行会新增收货或库存数据的专项测试。
