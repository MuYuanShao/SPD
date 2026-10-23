# SPD 项目导图

源码基线：`01422d42`。整理日期：2026-10-23。

双击 [项目导图.html](项目导图.html) 可离线浏览两张导图，支持缩放、搜索、节点说明、SVG导出和打印。

- [项目功能思维导图.svg](项目功能思维导图.svg)：工作台、7个一级业务菜单、技术与运行支撑。
- [进销存业务导图.svg](进销存业务导图.svg)：目录审批、采购收货、中心库、散货/定数包/高值三条路径、科室签收消耗、规则触发结算。
- [项目功能思维导图.opml](项目功能思维导图.opml)：可导入支持OPML的思维导图工具继续编辑。
- [项目功能思维导图.mmd](项目功能思维导图.mmd) 和 [进销存业务导图.mmd](进销存业务导图.mmd)：可编辑 Mermaid 源文件。

## 图片版

- [项目功能思维导图.png](项目功能思维导图.png)
- [进销存业务导图.png](进销存业务导图.png)

## 关键阅读口径

1. 图中菜单来自当前 featureCatalog，未把未挂载的兼容别名当成新增菜单；工作台和技术支撑为实际页面/架构归纳。
2. 中心库在拣配出库时扣减；目标科室库在签收时增加。打包不增加基本单位总量。
3. 高值在 HIS/手麻/手术计费回传成功后扣减科室库存，领用与患者绑定不是计费。
4. 结算只在对应批次快照规则命中后生成：入库结算、科室消耗结算、实销结算。相同来源不可重复结算。
5. 定数包数量与基本单位数量分别展示；包内基本数量只换算一次。包参考价=最新已审批医院目录采购单价×包内基本数量。
6. 目录最终审批才触发事务调价；任意来源已生成结算明细的包锁价，不重写历史成本、库存事件和已有结算。
7. 全部物理数量为整数；金额价格保留小数。混合供应商包必须全部来源结算完成才标记整包已结算。
8. 库存余额查询与交易流水分离；可打包散货快照不是已取消的库存事件快照业务模块。

## 主要依据

- `frontend/src/config/featureCatalog.ts`、`frontend/src/views/DashboardView.vue`
- `CONTEXT.md`、`docs/ARCHITECTURE.md`、`docs/adr/0002-quota-package-catalog-pricing.md`
- `OperationalDeliveryModule`、`OperationalConsumptionModule`、`OperationalHighValueModule`
- `QuotaPackageTraceFlowService`、`QuotaPackagePricingService`、`SettlementPointService`

此处为代码现状归纳；菜单节点存在不表示外部系统已经配置可用，也不替代上线验收。
