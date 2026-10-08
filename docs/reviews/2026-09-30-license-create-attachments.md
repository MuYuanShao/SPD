# 新增证照附件上传

商品证照、供应商证照、厂家证照及合同管理的新增弹窗均增加附件选择入口。支持多选PDF、PNG、JPG/JPEG、GIF、WebP；显示文件名及大小，保存前可以移除。取消新增清空待上传文件。

保存复用真实证照创建和附件上传接口：先取得证照ID，再逐个上传文件。全部完成后提示成功；保存后可从列表附件入口阅览和下载。

该流程包含多个请求。若附件上传失败，已保存的证照保留，弹窗保留未确认上传成功的文件并提示重试；重试更新同一证照并继续剩余文件，不重新创建证照。上传过程禁止关闭、修改和重复提交。既有接口按每次附件上传增加证照版本，前端同步版本以便失败后继续保存。

验证：

- 前端最终构建、UTF-8及Vue SFC检查通过。
- 新增上传专项6项及固定业务页面6项，共12项Playwright回归全部通过。浏览器测试使用真实登录及Vue页面，证照写接口使用模拟响应，覆盖四类新增、附件移除、保存后图片阅览、部分失败重试及取消清空。
- 既有证照服务单元测试及真实MySQL生命周期测试共3项通过。真实数据库用例验证附件关联、历史记录、文件读取及删除后访问限制，业务数据最终回滚，文件写入临时目录。

源码：frontend/src/views/master-data/LicenseManagementView.vue。
测试：tests/e2e/license-create-attachments.spec.ts。
日志：output/license-create-final-ui.log、output/license-create-build.log、output/license-create-mysql.log。
