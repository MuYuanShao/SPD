# 移动签收接口接入与验收

首批接入能力为 `SIGN_DELIVERY`：登录与上下文、配送任务、实物码核对、预校验、幂等整单签收及回执查询。拣选、消耗、Android 原生能力不属于本次后端合入。

## 启用配置

所有环境默认关闭移动业务。测试后端启动前设置：

```powershell
$env:SPD_MOBILE_ENABLED='true'
$env:SPD_MOBILE_SERVER_ID='spd-test-172-16-2-153'
# 使用受控配置系统注入 SPD_MOBILE_REVIEW_SECRET，至少 32 字节。
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/backend-control.ps1 restart
```

未配置核对密钥时采用进程内随机密钥；服务重启后未提交摘要失效，必须重新校验。多实例部署必须配置同一受控密钥。令牌、密码和核对密钥不可写入验收报告或 Git。

启动自动执行 V80，新增 `mobile_operation_receipt`，不修改库存表或现有权限授予。权限沿用 `picking-delivery:write`，科室/库房遵循现有数据范围；无此权限的账号不能执行移动签收。`/mobile/context` 可返回空能力列表。

V81 仅在权限定义缺失时补建 `picking-delivery:write`，不恢复被明确禁用/删除的定义，也不自动授予任何角色。运行库中已发现该定义缺失；管理员需为执行签收的账号配置权限。切勿把所有能登录的账号视为有签收权限。

## 接口顺序

1. `POST /api/auth/login`，随后读取 `/api/auth/me`。
2. `GET /api/mobile/context` 获取当前能力和合法科室库房。
3. `GET /api/mobile/tasks?kind=SIGN_DELIVERY&deptId=...&warehouseId=...`，状态为 picked 或 signed。
4. `GET /api/mobile/tasks/SIGN_DELIVERY/{id}?deptId=...&warehouseId=...`。
5. 包码/唯一码通过 `POST /api/mobile/scans/resolve` 在当前配送单内核对。
6. `POST /api/mobile/operations/validate`，获得五分钟有效的 reviewHash 和完整摘要。
7. 人工确认后 `POST /api/mobile/operations`；超时用原操作号查询 `/api/mobile/operations/{operationId}`。

提交载荷包括 operationId（UUID）、deviceId、kind、taskId、deptId、warehouseId、packageIds、traceCodeIds、looseConfirmed，以及预校验返回的 reviewHash。包/高值必须核对精确集合；散货必须确认实收。

## 验证方法

普通后端测试使用 `backend/mvnw.cmd test`。真实接口验收使用：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-mobile-isolated.ps1
```

独立验收脚本需要本机 MySQL Server 8.4 程序，使用 13316 端口和 output 下新数据目录；初始化随机密码、执行独立 Flyway 与 HTTP 测试后关闭。不会使用 ISPD 业务库。日志保存在 `output/mobile-mysql-*/acceptance.log`。

测试覆盖：真实登录、上下文和分页；预校验与签收；相同操作号重试和回执回读；同 ID 不同载荷拒绝；双请求并发仅一次写入；电脑端抢先签收后移动提交回滚；缺少实收确认或权限被撤销时不写入。

核对数据库：原库拣选后为 6、目标库签收后为 4；签收入库事件仅一条；成功回执仅一条。提交失败时不得残留 pending 回执。验收测试数据在独立库中清理，不在现有库存中做测试补偿。

## 当前边界

- 本批尚未执行手机/PDA真机、离线队列和 APK 验收。
- 业务写入测试仅证明所执行的散货签收场景；定数包和高值仍需逐类真实数据验收。
- 不支持把多个商品合并的历史 MULTI 配送视为本批单商品任务。
- 回执接口的 unknown 表示尚未确认，客户端不能因此创建新操作号重复提交。
