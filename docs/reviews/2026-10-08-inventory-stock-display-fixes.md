# 2026-10-08 库存显示问题修复与回归

## 修复结果

全流程测试中记录的3个库存显示问题均已修复，修改位于 `InventoryService` 的只读查询。

| 问题 | 原因 | 修复后口径 |
|---|---|---|
| 中心库定数包有行但 total=0 | 多字段 COUNT(DISTINCT ...) 忽略科室为NULL的分组 | 对与数据查询相同的分组结果计数，支持中心库和分页 |
| 科室已签收高值不显示 | 仅允许 in_stock；按批次余额连接未限定码的当前库房 | 纳入 in_stock/requisitioned/signed/patient_bound，按 trace_code_id 关系与 current_warehouse_id 定位，每码1件 |
| 科室定数包金额翻倍 | 签收包已恢复到库存余额，又作为散货重复相加 | 真实散货=max(无货位余额-同库房同商品全部已签收包数量,0)，包内数量只计算一次 |

通用库存余额查询也移除签收包的重复累加：库存余额+尚未签收的中心库在库包数量。中心库待打印/可用包继续计入库存。

唯一码查询过滤消耗、结算和配送中的码；UDI优先读取逐件追溯码记录，不用收货行UDI覆盖单件身份。科室一批多件时每码数量1、金额为该批次单价。

## 真实MySQL回归

新增 `backend/src/test/java/com/hospital/spd/supplychain/service/InventoryStockQueriesMysqlTest.java`。

测试在独立连接内创建MySQL临时表，直接运行服务SQL，连接关闭即删除，不修改业务表、业务数据或持久化数据库结构。正常单元测试不默认开启。

7个用例修复前全部失败，修复后全部通过：

1. 中心库科室为NULL，两个模板分组正确计数、每页1行并稳定翻页。
2. 科室已签收2包=10支，余额10支，单价2元，显示散货0、金额20元。
3. 同一商品跨两个模板的签收包均从散货余额中扣除，额外3支只识别为3支散货。
4. 商品编码、模板编码筛选与分页总数一致。
5. 通用库存余额正确计入中心库可用/待打印包，但不重复计算科室签收包。
6. 同批次跨两个库房，中心库在库码及科室signed/patient_bound码只归属当前库房，每码1件。
7. 按逐件UDI筛选，排除consumed与delivery_picked状态的码。

运行方式（从backend目录，使用已配置的SPD_DB_USERNAME/SPD_DB_PASSWORD）：

```powershell
$env:SPD_INVENTORY_QUERY_MYSQL_TESTS='true'
./mvnw.cmd "-Dtest=InventoryStockQueriesMysqlTest,InventoryServiceTest" test
```

定向验证22项通过：7项真实MySQL用例+15项库存单元测试。

## 完整回归和运行环境

- 默认后端完整测试690项，0失败、0错误，43项选择性集成测试跳过；上述7项MySQL用例已在独立定向运行中实际执行。
- 固定业务UI冒烟6项通过。
- 后端已重启，localhost:1820 页面读取修复后的真实API。
- 本次仅修改后端查询和测试，没有修改前端源码、业务状态或结算数据。

## 既有测试数据只读核验

测试标识 `FLOW261008757656`：

- 中心库定数包：total=1、rows=1、6包=30支、散货0、金额60元；第二页为空但total仍为1。
- 中心库高值：剩余2个唯一码，每码1件、100元；已消耗的2码未混入库存列表。
- 测试科室已完成全部消耗，余额仍为0，唯一码列表为空符合实际状态。
- 原4张待确认结算单仍为220元，未改动。
- 实际页面已验证定数包分页和唯一码行显示，未发现浏览器pageerror。

既有数据已全部消耗，没有仍处于signed的科室高值单元，因此signed/patient_bound显示与科室包金额以真实MySQL临时表回归覆盖，不虚构现有业务状态。

证据：`output/playwright/inventory-stock-fixes-20261008/verification.json`、`quota-stock.png`、`unique-code-stock.png`。只读浏览器复查脚本：同目录 `verify-live.mjs`。
