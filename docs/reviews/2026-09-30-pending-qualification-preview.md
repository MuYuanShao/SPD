# 待审批目录资质证照附件阅览

待审批目录每行新增“资质阅览”按钮，审批详情中的资质信息区复用同一附件组件。支持图片及PDF弹窗阅览、下载、刷新和空状态；文件读取失败保留附件列表并显示中文错误。

附件范围：申请直接上传的资质文件，以及当前关联商品、供应商、厂家证照和匹配合同编号的合同附件。关联优先使用主体ID；历史缺少ID的证照按主体编码匹配。列表显示来源、主体、证照编号、状态及有效期，过期或失效证照仍可供审核阅览。删除的证照或附件不返回。

新增只读接口：

- GET /api/pending-product-applications/{applicationNo}/qualification-attachments
- GET /api/pending-product-applications/{applicationNo}/qualification-attachments/{attachmentId}/file

文件读取同时校验申请访问权限及附件与申请的关联，不能仅凭附件ID读取不相关证照。复用已有sys_attachment和license_document存储，不新增表，不改变审批流程和提交时路由快照。关联证照展示当前版本，申请上传附件仍保留原业务归属。

验证：

- 真实MySQL及文件读取验证通过：申请、商品、供应商、厂家、合同五类文件读取；未关联文件、删除附件和无审批权限账号被拒绝。测试数据回滚、文件位于临时目录。连同原附件验证共2项通过。
- 后端完整回归679项：失败0、错误0、条件跳过36。
- 前端构建、UTF-8和Vue SFC检查通过。
- 浏览器阅览专项4项与固定业务回归6项，共10项通过。专项使用真实认证、列表及详情API，附件响应使用夹具，覆盖图片、PDF、下载、权限错误及空状态。
- 未Mock的真实浏览器验证新增列表按钮及真实关联附件接口返回HTTP200/code0；所检申请当前无关联附件，正常显示空状态。

相关证据：output/qualification-focused.log、output/qualification-all-tests.log、output/qualification-ui.log、output/qualification-real-api.log、output/qualification-build.log。
