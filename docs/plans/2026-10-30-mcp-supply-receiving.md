# MCP 医院订单供货与 SPD 待收货接入

MCP 接收医院采购计划及原采购订单后，供应商在“采购对接 → 医院计划与结算 → 医院订单供货”填写供货单号、医院收货库房、基本单位整数数量、批次、生产日期、有效期及可选 UDI（最多120字符，独立于SPD生成的唯一码）。供货基于原医院订单及明细，不复用 MCP 原生单据 ID。

## 状态与业务边界

供货提交 → MCP 持久回传队列 → SPD 创建 receiving_order 草稿 → 院方在现有“收货验收 / 待收货”检查、编辑和审核 → 按合格数量入库及原结算点处理。MCP 显示 SPD 收货单号，可刷新院方的待收货、已验收或拒收状态。

已批准的验收回执包含院方实际收货、合格及不合格数量，按基本单位分别展示；不同单位不混加。原供货申报明细可分页只读查询，与院方调整后的验收结果分开保留。刷新可收数量不改变正在填写的供货单号及幂等身份。

回传不会批准收货、增加库存、创建付款事实或改变院内结算价格。供货字段不接收供应商价格，收货验收仍使用 SPD 现有权威价格逻辑。院方可以调整验收数量和库房，但不能将导入草稿换到别的采购订单，或增加原供货单没有申报的采购明细及数量。

## 持久性与隔离

- 双端使用固定 messageId / Idempotency-Key 和规范化摘要。相同消息返回原收货单，异内容冲突；同医院、供应商、采购订单及供货单号不能重复建单。
- 一张供货单对应一个医院采购订单，可分多个批次，也可对订单分批供货。不同采购订单可分开使用同一供应商业务单号。
- SPD 校验机构、供应商、订单状态、明细归属和单位，锁定商品及采购订单后检查已收数量与待收货占用。历史未明确绑定采购明细的草稿也会保守占用该商品数量。
- 网络错误按原消息退避重试，持久租约支持异常恢复；参数错误终止为“SPD校验拒绝”，可更正重提。收到回执仅表示创建待收货单，不能显示为已入库。
- MCP 用户需绑定该医院实例下的来源供应商；管理员也不能替未绑定的供应商发起供货。服务器重建供应商及外部操作人字段，不信任客户端声明。接口及查询均按租户、实例、供应商隔离。
- 回传目标地址随消息冻结，实例地址改变时停止投递并提示核对。密钥可正常轮换，不写入出站表。

## 部署

- SPD 迁移 V106__mcp_supply_receiving.sql，MCP 迁移 V29__hospital_order_supply.sql，通过两端正常 Flyway 执行。
- 沿用第三期功能开关，另设置 SPD_MCP_SUPPLY_ENABLED=true、APP_PHASE3_SUPPLY_ENABLED=true；新增供货开关默认关闭。
- MCP 供应商需 integration:phase3:read + integration:supply，并配置实例和供应商数据范围。
- SPD 服务账户需 mcp-phase3:supply 和 receiving-order:create。现有 shared token、机构和服务账户认证继续有效；不得将服务账户的收货审核能力暴露为回传操作。
- 不恢复已移除的旧配送 / 运输菜单与权限。本功能直接操作医院上传订单，使用独立 integration:supply 权限。

## 接口

SPD 专用安全链：

- GET /api/mcp/phase3/supply-options：院内权威库房和实时剩余数量，要求 organization、orderNo、supplierCode。
- POST /api/mcp/phase3/supplies：机构绑定的共享密钥和 Idempotency-Key，创建草稿。
- GET /api/mcp/phase3/supplies/{messageId}：机构及供应商匹配的当前收货回执。

MCP /api/integration/instances/{id}/phase3 下：

- GET purchase-orders：供应商范围内医院上传订单分页。
- GET purchase-orders/{orderNo}/supply-options：实时读取源端可供货选项。
- POST / GET purchase-orders/{orderNo}/supplies：提交供货 / 分页查询回传记录。
- POST supplies/{messageId}/retry：重试网络或配置失败的原消息。
- POST supplies/{messageId}/refresh：刷新已经接收的 SPD 收货单状态。
- GET supplies/{messageId}/items：分页读取不可变的原供货批次申报。

验收必须覆盖：供货先产生待收货且库存不变；院方验收后才入库；重复及响应丢失不重复建单或入库；并发超量、错误供应商、错误明细、错误单位和无权限均拒绝；结算与原计划上传功能保持正常。
