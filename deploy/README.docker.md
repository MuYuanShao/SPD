# SPD Docker 一键部署

此目录与仓库根 Dockerfile 是新的容器部署入口。Windows双击仓库根“一键Docker部署.bat”，或运行 deploy/start.ps1；Linux运行 sh deploy/start.sh。

## 运行形态及依赖

| 组件 | 容器包含内容 | 宿主机是否安装 |
| --- | --- | --- |
| MySQL | MySQL8.4服务器/客户端、utf8mb4、上海时区、触发器创建所需设置 | 否 |
| 数据库初始化 | 独立迁移账号执行Flyway全部迁移，再设置随机初始管理员密码 | 否 |
| Java后端 | JRE17、Spring Boot3.5.16、Spring JDBC/Security/Validation/Actuator、JWT0.13.0、Flyway10.22.0、MySQL Connector/J、POI5.5.1及其传递依赖 | 否；全部在可执行JAR和镜像中 |
| 后端系统库 | curl、CA证书、tzdata、fontconfig、FreeType、DejaVu及Noto CJK中文字体、tar；Java开启headless与UTF-8 | 否；Excel字体度量和中文输出不依赖宿主机字体 |
| 管理端 | Vue3、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts、图标、拼音/拖拽等，准确版本由package-lock.json锁定 | 否；构建产物在Nginx镜像 |
| 移动端H5 | mobile自己的package-lock.json锁定Vue、Capacitor Web依赖，构建到/mobile/ | 否；服务端不包含Android SDK/Gradle，生成APK属于单独构建任务 |
| Web入口 | Nginx stable-alpine、SPA路由回退、/api反向代理、/mobile/入口、30MB上传、180秒读取超时 | 否 |
| 构建工具 | Maven3.9.9/JDK17、Node22/npm，使用多阶段构建和缓存 | 否；只在镜像构建阶段使用，离线部署无需构建工具 |
| 可选AI | Ollama官方镜像；配置模型默认qwen3-vl:8b；模型保存在独立命名卷 | 不启用AI时不需要；GPU不是核心部署前提 |
| HIS/手麻/手术/MCP | 外部院内业务系统；仅打包本系统客户端/接口代码 | 不伪造外部系统，地址/凭据由院方提供 |

当前项目没有强制Redis、RabbitMQ或宿主机MySQL依赖，未为这些未使用服务添加容器。Docker基础镜像标签可随上游更新；发布镜像包中的images-metadata.jsonl记录实际镜像ID/架构，迁移时使用已导出的镜像，而非重新拉取浮动标签。

## 宿主机要求

- Docker Engine（Linux）或Docker Desktop（切换为Linux容器），Compose至少2.24.4。Windows需要可用的WSL2/虚拟化后端。
- Windows入口使用系统PowerShell5.1及以上；Linux使用POSIX sh及系统自带od/sed/sha256sum等基本工具。
- 在线首次构建需访问镜像仓库、Maven Central、npm和发行版软件源；启用AI还需下载模型。离线目标仅需复制完整镜像包。
- 容量取决于数据库、附件和模型；AI模型占用额外磁盘/内存。Docker镜像包须与目标CPU架构匹配。
- HTTP默认1820端口；已有localhost:1820开发服务时，请在私有.env中改SPD_HTTP_PORT，例如21820，不停止或覆盖已有服务。

Docker官方Windows要求及依赖就绪机制：[Windows安装要求](https://docs.docker.com/desktop/setup/install/windows-install/)、[Compose启动顺序](https://docs.docker.com/compose/how-tos/startup-order/)。可选AI参考[Ollama Docker说明](https://docs.ollama.com/docker)。

## 一键在线部署

Windows：

```powershell
.\deploy\start.ps1
# 或双击仓库根“一键Docker部署.bat”
```

Linux：

```sh
sh deploy/start.sh
```

首次自动创建私有deploy/.env，随机生成数据库运行/迁移/root密码、JWT密钥、初始管理员密码及移动作业签名密钥。Windows限制文件ACL，Linux使用umask077；脚本不输出密码，不把密钥写进镜像。已有.env绝不自动覆盖。若宿主机进程已设置同名变量，Docker Compose仍按其标准规则优先使用进程环境，部署前应确认这些值属于本次部署。

访问管理端http://localhost:1820；移动端H5 http://localhost:1820/mobile/。初始账号admin，密码查看私有.env的SPD_INITIAL_ADMIN_PASSWORD。移动端首次需在其服务器设置中填写可访问的/api地址；原生APK仍按移动端交付流程安装。Nginx保留公开Host及端口，后端恢复转发头，局域网同源访问无需逐个白名单配置服务器IP。浏览器同源访问不需要开放数据库或后端端口；原生客户端如需跨域，在SPD_CORS_ALLOWED_ORIGINS中配置明确来源。

默认生成的移动配置具有持久server-id和签名密钥。若不使用移动接口，设置SPD_MOBILE_ENABLED=false。首次初始化会替换迁移内的默认管理员种子密码并禁用未改密的演示账号，重新启动不会覆盖操作人员已修改的密码。

启动链路：MySQL认证健康 → migrate成功退出 → bootstrap成功退出 → 最小权限后端健康 → Nginx健康 → 验证入口/api/health。数据库和后端只在Compose内部网络开放；附件与日志、数据库持久化。

可先只生成/验证配置，不启动容器：

```powershell
.\deploy\start.ps1 -InitOnly
```

```sh
sh deploy/start.sh --init-only
```

自定义.env：Windows -EnvFile D:/secure/spd.env；Linux --env-file /secure/spd.env。保留已有镜像不构建：-NoBuild / --no-build。

## 可选AI一键部署

```powershell
.\deploy\start.ps1 -WithAi
```

```sh
sh deploy/start.sh --with-ai
```

启用ai profile后，拉取Ollama并初始化配置模型；后端等待模型初始化成功。默认CPU模式，无需改变核心数据库/后端部署；GPU加速另按显卡驱动和NVIDIA Container Toolkit配置。已有可访问模型服务可填写SPD_AI_BASE_URL并不启动本地AI。模型下载耗时较长时提高-WaitTimeout / --wait-timeout。模型许可由实际下载模型提供，导出时随包保存。

## 打包为完全离线镜像包

在有正常Docker引擎及网络的构建机运行：

```powershell
.\deploy\package.ps1
# 包含模型及模型缓存：
.\deploy\package.ps1 -WithAi
```

```sh
sh deploy/package.sh
sh deploy/package.sh --with-ai
```

输出为新的output/docker-bundle/spd-版本-时间目录，包含：

- images.tar：MySQL、后端及Nginx（管理端+移动端H5）全部运行镜像及其依赖层；WithAi另含Ollama镜像。
- models.tar及model-license.txt：仅WithAi导出，包含已实际下载的模型缓存和许可证；docker save本身不包含卷内模型权重。
- images-metadata.jsonl：镜像身份、架构及元数据。
- SHA256SUMS：全包文件校验清单。
- Compose、跨平台启动脚本及说明；不含构建机私有.env、数据库备份或业务附件。

复制整个目录到目标服务器，双击start.bat或运行sh start.sh。脚本先校验文件，再docker load，随后使用--no-build --pull never启动；无需目标机安装Java/Node/Maven或连接软件源。WithAi包同样使用-WithAi / --with-ai启动，先恢复空模型卷；已有模型卷不覆盖，缺少离线模型时明确失败而不假装已完成AI初始化。

源码部署包与images.tar镜像包不同：源码包仍需要首次联网构建；只有package脚本实际成功生成的完整镜像包才可离线运行。打包脚本失败不代表已有完整镜像包，请勿将空目录作为交付件。

## 持久化与运维

- spd-mysql-data：数据库；spd-application-data：证照、审批与采购附件等/app/output；spd-application-logs：文件日志；*-ollama-models：可选AI缓存。
- 普通stop/down不会删除命名卷；不要运行down -v来升级或重启。
- 版本升级应先备份并在维护窗口停止本项目旧容器，再执行启动脚本；不承诺不停机数据库升级。
- .env与原数据卷是一组部署身份，重装不能重新随机生成密码。修改COMPOSE_PROJECT_NAME会选择另一组卷，不是迁移原库。
- 镜像不含业务数据，迁机还需分别备份数据库和附件卷。导入旧库必须先备份，并执行正常Flyway校验；不自动repair迁移历史。
- 后端只使用SELECT/INSERT/UPDATE/DELETE运行账号，启动校验拒绝DDL权限；Flyway只在独立一次性迁移服务中使用迁移账号。
- 上传默认30MB；生产HTTPS由上层反向代理或网关终止，证书不烘焙到镜像。无证书时不能声称已完成生产TLS部署。

查看状态/日志：

```sh
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps -a
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs --tail=100 mysql migrate bootstrap backend frontend
# 日常停机保留数据
docker compose --env-file deploy/.env -f deploy/docker-compose.yml stop
```

失败时先检查Docker引擎、端口占用、migrate/bootstrap日志、私有.env与现有卷是否匹配。脚本不会删除已有卷，也不会通过关闭Flyway验证来掩盖不兼容。
