# 商品证照预览与统一台账修复

日期：2026-10-30。对应 [前轮实测发现的两个问题](2026-10-30-catalog-license-delivery-preview.md)。

后续部署状态：已按用户要求将本地 ISPD 从 V101 升级至 V105，见 [实际迁移记录](2026-10-30-v105-local-migration.md)。下文保留开发及隔离验收时的记录。

## 修复结果

1. **院内商品详情证照预览恢复。** 后端返回商品专属附件地址，接口要求当前商品确实拥有该有效附件；不再依赖待审批模块权限。前端使用带登录态的 API 客户端读取 Blob，支持 PDF 与图片，显示加载或错误状态；关闭、切换商品、离开页面时回收预览 URL，防止迟到请求复用旧文件。跨商品、待审批文件和已退役附件均不能通过该接口读取。
2. **MCP 已批准证照进入统一台账。** 审批发布事务同时更新 license_document、附件和不可变历史。以 source_product_id + source_license_id 为稳定身份；改号、续期或再次下发不会创建重复台账。未批准、被驳回及回滚事务不使新证照生效；解绑退役，重新关联仍沿用原台账身份。

来源证照在页面标记为“MCP审批同步”。其有效字段和附件由 MCP 下发及院内审批维护，后台拒绝直接编辑、续证、删除或上传替换。院内自行维护的证照仍沿用原流程。历史附件通过所属证照和版本共同校验，避免把已经退役的文件重新当成当前附件，且不能跨证照或跨版本任意读取。

## 迁移与部署

新增 Flyway **V105__mcp_approved_license_ledger.sql**，部署并启动新版后端时按正常迁移执行。

迁移增加来源关联字段和唯一约束，将已有 mcp_license_approved 中的已批准记录补齐到统一台账，关联真实附件并保存版本 1。待审批和被驳回意图不会被回填。文件继续保存在原私有目录，台账通过相对路径引用，不生成伪造证照或复制无关文件。

无需手工修改业务数据库。测试使用临时数据库；本轮没有对业务数据库执行迁移，也没有提交 Git commit。

## 测试结果

| 验证 | 结果 |
| --- | --- |
| 真实 MySQL 专项 | 9 项全部通过：V104→V105 已批准数据回填、首次审批、续证替换与历史文件、幂等发布、未批准不生效、事务回滚、来源写保护、解绑重连、商品附件归属和退役校验 |
| 后端广泛回归 | 890 项，0 失败、0 错误、152 跳过，打包成功 |
| 前端类型检查及构建 | 通过；仅保留既有包体积提示 |
| 双端隔离联调 | 145 项全部通过 |
| Chrome 页面复验 | 院内商品详情及独立证照管理的 PDF、PNG 均正常显示 |

专项命令：`python scripts/test-mcp-phase3-review.py --tests=McpLicenseLedgerMysqlTest`。日志：[tests.log](D:/SPD-PR/output/phase3-review-tests/phase3-review-20261030-112019/tests.log)。脚本创建一次性 MySQL，不连接业务库；普通单元测试默认跳过此组用例。

广泛回归沿用前轮命令 `mvn -f backend/pom.xml "-Dtest=!PendingProductApplicationControllerTest" package`。152 项跳过由原有 133 项、前轮第三期专项 10 项及本轮专项 9 项组成；本轮 9 项已在独立脚本真实执行。此前耗时较长的 PendingProductApplicationControllerTest 未重复执行。

双端报告：[report.json](D:/SPD-MCP-UI/test-results/dual-system-20261030-112247/report.json)。

## 浏览器证据

验收商品 PREVIEW_30777399（MCP 9002），首次申请 SP20261030001：审批前证照台账 total=0，正常审批后台账 total=2，分别为 PDF 注册证与 PNG 生产许可证，每条 attachmentCount=1。

- [院内商品 PDF 正常预览](D:/SPD-PR/output/playwright/license-preview-fixed/hospital-pdf.png)
- [院内商品 PNG 正常预览](D:/SPD-PR/output/playwright/license-preview-fixed/hospital-png.png)
- [统一证照台账可查询两条记录](D:/SPD-PR/output/playwright/license-preview-fixed/ledger-list.png)
- [证照管理 PDF 正常阅览](D:/SPD-PR/output/playwright/license-preview-fixed/ledger-pdf.png)
- [证照管理 PNG 正常阅览](D:/SPD-PR/output/playwright/license-preview-fixed/ledger-png.png)

院内商品文件请求实际使用 `/api/master-data/hospital-products/PREVIEW_30777399/attachments/{id}/file`；独立台账使用 `/api/licenses/attachments/{id}/file`，均返回真实文件。PNG 解码尺寸为 1100×760；PDF 显示完整单页内容，不再出现 SPD 页面嵌套。

再次下发证照并通过 SP20261030002 后，仍为同两个 license_id（1、2），revisionNo 均为 2，每条仍只有一份当前附件；版本历史为 [2,1]，版本 1 PDF 仍可经版本专属接口读取，大小 50765 字节。接口证据：[重发与历史结果](D:/SPD-PR/output/playwright/license-preview-fixed/reapproved-history.txt)。

所有临时服务及浏览器在验收完成后清理。旧测试中的两项页面问题已修复，旧报告保留为修复前证据。
