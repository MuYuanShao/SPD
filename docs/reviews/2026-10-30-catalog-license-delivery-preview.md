# MCP 商品目录、证照下发及 SPD 预览实测

> 后续状态：本报告记录的是修复前结果。两项问题已修复并完成复验，见 [修复与验证记录](2026-10-30-license-preview-ledger-fixes.md)。

日期：2026-10-30。使用当前代码和构建，在独立 MySQL、临时账户及专属前后端端口完成实际 API 与 Chrome 页面测试。未修改业务代码或业务数据库。

## 样本和链路

- MCP 商品：9002，编码 PREVIEW_28814481，名称“商品证照预览验收_28814481”；初始规格 10ml，采购价 2.3456，单位“支”。
- PDF 注册证与 PNG 生产许可证：有效期至 2027-10-30，均为明确标记的测试文件。
- SPD 首次接收申请 SP20261030001，正常页面“一级审批通过”后进入院内目录。
- MCP 页面“仅重新下发证照”成功进入 SP20261030002；再将商品规格修改为 20ml，从页面点击“商品及证照下发”成功更新该待审批申请，SPD detail.specModel 实际为 `20ml / PREVIEW`，同时仍有两份证照附件。
- 对已批准商品不修改任何字段时再次完整下发，系统提示“未检测到与医院目录当前数据的差异，不需要提交信息变更审批”；这是正常业务校验，与初次接收成功及独立证照下发成功不冲突。

## 结果

| 检查 | 实测结果 |
| --- | --- |
| 商品目录下发 | 成功，进入真实待审批目录，审批后院内商品名称、编码、注册证号及四位采购价可查询 |
| 独立证照下发 | 成功；MCP 页面返回 processed，SPD 真实接收 PDF 和 PNG |
| 文件完整性 | 两份文件审批前及审批后 SHA-256、字节数、MIME 类型均与原文件一致 |
| 待审批商品详情 PNG 阅览 | 成功，图像 complete=true，naturalWidth=1100、naturalHeight=760 |
| 待审批商品详情 PDF 阅览 | 成功，Chrome 弹窗实际渲染单页证照内容与页码 1/1 |
| 审批后院内商品详情 PDF / PNG“查看” | **失败**，iframe 打开的是前端 SPD 页面而非文件 |
| 独立“证照管理”查询已接收商品 | **未显示**，商品编码筛选 API 返回 rows=[]、total=0 |

原 PDF：50765 字节，SHA-256 `38bbc964512e5faaba3b3d851c30d0cc980d05ebb86bfaf7bf8d70a3a1ac0773`。

原 PNG：41204 字节，SHA-256 `24e64e2ed04fa04b6d65b56729bf008ca143869be4604ed7c63752fa346523f9`。

## 问题一：院内商品详情使用了错误预览路径

复现：批准 SP20261030001 → 院内商品 PREVIEW_28814481 → 附件信息 → PDF 或 PNG“查看”。

后端返回的 fileUrl 为 `/pending-product-applications/attachments/2/file`（PDF）或 `/pending-product-applications/attachments/1/file`（PNG）。[ProductDetailView.vue](D:/SPD-PR/frontend/src/views/master-data/ProductDetailView.vue:296) 直接将此值放进 iframe.src，浏览器访问前端地址 `/pending-product-applications/...`，没有经过 `/api`，实际渲染 SPD 前端页面。正确的带鉴权 `/api/pending-product-applications/attachments/{id}/file` 请求可以获取原始文件，因此不是接收文件损坏或审批丢失附件。

建议复用带登录态的 Blob 读取方式生成临时预览 URL，而不是只拼接路径；关闭或切换预览时回收 URL。

## 问题二：接收证照尚未进入独立证照台账

复现：上述商品已批准且院内详情显示两份附件 → 证照管理 → 商品证照 → 搜索 PREVIEW_28814481，列表为空。

实际查询 `/api/licenses?type=product&keyword=PREVIEW_28814481&page=1&size=20` 返回 total=0。接收及审批链路保存的是 mcp_pending_license / mcp_license_intent / mcp_license_approved 和商品附件；[LicenseService.java](D:/SPD-PR/backend/src/main/java/com/hospital/spd/licenses/LicenseService.java:94) 的独立证照页面读取 license_document，目前这两套记录未接通。因此不能将“商品附件接收成功”视作“独立证照管理也已同步成功”。

建议在审批发布阶段建立来源证照与统一台账的稳定映射，统一维护所有者、有效期、附件和版本；续证或重发应更新原来源关系，避免重复台账。

## 证据

- [待审批 PDF 正常预览](D:/SPD-PR/output/playwright/catalog-license-preview/pending-pdf-preview.png)
- [待审批 PNG 正常预览](D:/SPD-PR/output/playwright/catalog-license-preview/pending-png-preview.png)
- [院内商品 PDF 错误预览](D:/SPD-PR/output/playwright/catalog-license-preview/hospital-pdf-preview.png)
- [院内商品 PNG 错误预览](D:/SPD-PR/output/playwright/catalog-license-preview/hospital-png-preview.png)
- [独立证照管理查询为空](D:/SPD-PR/output/playwright/catalog-license-preview/license-management-empty.png)
- [页面商品下发成功](D:/SPD-PR/output/playwright/catalog-license-preview/mcp-catalog-dispatch-result.png)
- [页面独立证照下发成功](D:/SPD-PR/output/playwright/catalog-license-preview/mcp-license-dispatch-result.png)

接口结果、附件哈希及页面快照保存在 output/playwright/catalog-license-preview。测试运行目录：D:/SPD-MCP-UI/test-results/dual-system-20261030-104934；测试进程在完成后清理。两个页面问题本轮仅记录，尚未修复。
