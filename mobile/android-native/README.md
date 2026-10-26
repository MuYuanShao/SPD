# Android 原生能力实施说明

当前 Capacitor 页面承载登录、在线作业和原操作恢复。已接入的原生能力为 HTTP 和 Android Keystore 加密作业日志；此目录的 Kotlin 与 initial-scaffold 文件为设计草稿，不参与 APK 构建。摄像头、Room 与 WorkManager 尚待后续实施。

## 扫码

- CameraX 负责预览和权限生命周期。
- ML Kit Barcode Scanning 离线模型识别 QR、Code 128、GS1 DataMatrix、EAN/UPC。
- 扫描结果只生成 `ScanEvent(rawCode, source=camera, scannedAt)`，解析交给 `/api/mobile/scans/resolve`。
- 相机权限拒绝时保留 PDA 键盘和手工输入入口。

## PDA 键盘

使用隐藏输入框接收键盘输出，以 Enter 作为结束符；扫描缓冲与数量输入分离。禁止通过广播接收厂商私有协议作为 V1 唯一入口。

## 网络和同步

当前不自动提交离线业务。结果未知时通过 OnlineOperationFlow 查询原回执或请求服务器恢复；恢复沿用原 operationId 和业务内容，展示新摘要并由用户再次确认。后续网络监听和 WorkManager 必须复用此协议，不能另建一套重试状态机。

## Room/SQLite

旧 Preferences 明文队列原型已移除。当前日志由 SpdSecureStorePlugin 以 Android Keystore AES-GCM 加密保存，按服务器地址、serverId、用户和作业类型隔离。未来 Room/SQLite 队列须保留这些隔离、原编号恢复和事务规则，不保存密码。

## 证书

首版不要求在安卓设备安装医院 CA。服务器必须使用安卓系统默认信任的公开 CA 证书，并使用正式 DNS 域名访问。生产环境禁止 cleartext traffic、用户证书绕过和跳过 hostname 校验。若医院只能提供自签名证书，则该环境暂不能作为正式发布环境。

APP 保存 `environment` 与 `baseUrl` 两个运行配置。测试环境使用公网测试域名，正式环境使用医院内网域名；切换环境前必须先完成当前账号退出并确认本地没有未解决记录。队列记录按 `serverId + userId` 隔离，禁止把测试队列提交到正式服务器。

## 构建

```powershell
npm install
npm run cap:sync
cd android
./gradlew assembleRelease
```

在 Windows PowerShell 中使用 `..\..\scripts\check-mobile-build-env.ps1` 先检查环境。首次构建需要联网下载 Gradle、Android Gradle Plugin、CameraX、ML Kit、Room 和 WorkManager；医院内网应配置 Gradle/Nexus 镜像。Capacitor 同步后会把 `@capacitor/android` 作为 `:capacitor-android` 工程加入 Android settings。

APK 使用医院私有签名密钥；密钥不提交仓库。通过医院 MDM 或内部文件服务器分发，并提供 SHA-256、版本号、回滚 APK 和升级迁移说明。
