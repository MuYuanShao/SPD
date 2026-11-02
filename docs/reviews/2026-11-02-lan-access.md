# SPD-PR 局域网访问配置

日期：2026-11-02。

访问地址：**http://172.16.2.153:1820**。适用同一 `172.16.2.0/24` 网段，使用现有系统账号登录，原权限保持。

## 已完成配置

- 本机网卡“以太网4”地址为 `172.16.2.153/24`。
- 前端启动参数从 `--host 127.0.0.1` 改为 `--host 0.0.0.0`，监听1820。
- 前端仍通过相对路径 `/api` 代理到本机1818后端，其他电脑不需要把API地址改为其自己的localhost。
- 后端加入精确来源 `http://172.16.2.153:1820`，保留原localhost、127.0.0.1来源；没有使用通配CORS。
- Windows管理员进程已成功配置防火墙规则 `SPD-PR-LAN-Web-1820`：TCP入站1820，仅本机地址172.16.2.153、来源172.16.2.0/24。覆盖当前Public网络配置，无须改变网卡网络类别或关闭防火墙。
- 本次没有新增1818或MySQL端口放行，没有配置公网路由器转发。

## 启动持久化

局域网来源配置保存在 `output/lan-access/spring-lan.json`，不包含数据库或登录密码。

- `scripts/backend-control.ps1` 启动时自动读取该文件。
- 已有本地 `output/mixed-plan-deployment/restart-spd.ps1` 和 `output/local-supply-deploy/start-local.ps1` 也已适配该配置；后者前端启动参数保留局域网监听。
- 本机完整重启SPD前后端可执行 `output/lan-access/start-lan.ps1`；脚本先核对1818/1820的进程归属，只重启SPD，MCP保持运行。
- 防火墙规则已持久保存；维护脚本为 `scripts/configure-lan-firewall.ps1`，重新配置规则需要Windows管理员权限。

这不是Windows开机自启动服务。访问时本机及服务需保持运行。若网卡IP或网段变化，需要同步调整来源配置、防火墙范围和分享地址，不应直接修改网卡地址影响现有网络配置。

## 实测结果

- 监听核验：前端 `0.0.0.0:1820`。
- `http://172.16.2.153:1820` 返回200。
- `http://172.16.2.153:1820/api/health` 返回200。
- 后端针对该LAN来源的预检从配置前403变为200，Access-Control-Allow-Origin为精确LAN地址。
- Playwright直接通过LAN地址成功登录，打开真实采购计划列表并显示 `JH202610310001`；没有仅验证静态首页。
- 两个PowerShell脚本语法解析通过。此次无业务代码、数据库结构或权限变更，无需重建前后端。

页面证据：[局域网采购计划](D:/SPD-PR/output/playwright/lan-access/purchase-plan.png)。以上为本机使用LAN地址进行的浏览器验证及防火墙检查，未使用另一台实体电脑进行验收。
