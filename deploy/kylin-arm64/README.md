# 麒麟 V10 ARM64 独立离线安装包

此目录仅用于麒麟 V10 aarch64，不使用 Windows 安装包及 Windows JDK/MySQL。
发行包包含 ARM64 Docker 29.9.2、Compose 2.40.3、MySQL 8.4、Java 17 后端、管理端、移动 H5、数据库逻辑备份及附件。
不包含 AI 模型或 HIS/MCP 等外部系统；这些系统需要单独提供服务地址和凭据。

## 安装

上传完整的 `spd-kylin-v10-arm64-*.tar.gz`，在目标机运行：

```bash
tar -xzf spd-kylin-v10-arm64-*.tar.gz
cd spd-kylin-v10-arm64-*
sudo bash install.sh
```

目标机须为麒麟 V10 ARM64，具有 systemd、iptables、tar、gzip、sha256sum、flock、curl。
安装前检查 Web 端口未被其他程序占用。默认访问 `http://服务器IP:1820`。
Docker 运行在独立 Unix socket `/run/spd-docker.sock`，二进制在 `/opt/spd-kylin/runtime`，数据在 `/var/lib/spd-docker`。
安装器独立管理 SPD 的 Docker 服务，仅发布 Web 端口。院内防火墙规则可由运维配置。若要由安装器放行 Web 端口，安装时指定实际接入区域，例如 `sudo SPD_FIREWALL_ZONE=internal bash install.sh`。
数据库在空库中恢复。安装器核对数据库计数和附件归档内容，记录数据库名及两个数据卷的身份后才允许启动。非空库禁止覆盖，恢复中断后需由运维检查原库、附件及安装日志。重复安装使用相同发行包并保留原密码和数据。
业务数据是导出时的快照，后续 Windows 上产生的数据不会同步。切换正式业务前应停止源端写入，再重新导出并验证。

## 账号与配置

首次运行生成 `/opt/spd-kylin/app/.env`，权限由 root 的 umask 077 限制；勿公开或重新生成。
恢复的数据保留原业务账号密码。只有仍使用代码种子密码的 admin 会由初始化命令替换，初始密码见 `.env` 的 `SPD_INITIAL_ADMIN_PASSWORD`。
修改 Web 端口使用 `SPD_HTTP_PORT`，再运行管理脚本的 restart；对应调整防火墙规则。
浏览器使用同源 `/api`，不需要把服务器 IP 编译进前端。
移动 API 默认关闭；如需启用，核对稳定 server-id、签名密钥及客户端配置。
外部 HIS/MCP/AI 接口默认不随包启动，须另外配置并验证。

## 运维

```bash
bash /opt/spd-kylin/app/manage.sh status
bash /opt/spd-kylin/app/manage.sh logs
bash /opt/spd-kylin/app/manage.sh stop
bash /opt/spd-kylin/app/manage.sh start
bash /opt/spd-kylin/app/manage.sh backup
```

backup 持有运维锁，暂停原本运行的应用服务，完成备份和校验后恢复这些服务，再发布备份目录。
备份路径为 `/opt/spd-kylin/app/backups/时间`，包含敏感业务数据和密码，必须限制访问。
禁止执行 `down -v`、删除 `/var/lib/spd-docker` 或手工覆盖卷；这些操作会丢失数据。
安装日志、`data/data-manifest.json`、镜像清单及校验文件用于核对恢复结果。
HTTP 访问不等于已配置 HTTPS；正式 HTTPS 由院内网关或反向代理提供。

## 构建和验证

`Dockerfile` 的 context 包含同一源码版本构建的 `app.jar`、`web/`、`mobile/`、`mysql-init-runtime.sh`、`mysql-ensure-accounts.sh`、`mysql.cnf`、`nginx.conf` 和 `backend-entrypoint.sh`。构建端把该版本的完整 Git SHA 写入 context 中的 `source-revision`。镜像标签由该 SHA 与实际镜像 ID 生成。
构建机使用 ARM64 Docker 分别构建 mysql、web、backend 三个 target；导出全部运行镜像，目标安装不再下载或构建。
`bash build-package.sh CONTEXT DATA VENDOR OUTPUT` 生成独立目录和压缩包。DATA 提供 `ISPD.sql`、`attachments.tar`、`data-manifest.json` 和 `restore-counts.tsv`。停止源应用写入后，从同一快照导出这些文件。

- 使用 `mysqldump` 指定数据库名生成 SQL，省略 `--databases`，使转储可导入目标库名。已有包含 `USE` 或建库语句的转储需要重新导出。
- `attachments.tar` 的归档根为后端应用数据卷的内容。
- 使用 `mysql --batch --raw --skip-column-names` 查询源数据库，按下面的顺序写入 `restore-counts.tsv`。

```sql
SELECT 'flyway_schema_history', COUNT(*) FROM flyway_schema_history;
SELECT 'product', COUNT(*) FROM product;
SELECT 'sys_user', COUNT(*) FROM sys_user;
SELECT 'sys_attachment', COUNT(*) FROM sys_attachment;
```

在 ARM64 验收环境中，选用源快照内已知 ID 和 SHA256 的证照附件。安装后通过 API 下载校验，访问令牌从标准输入读取。

```bash
read -rs token
printf '%s\n' "$token" | bash verify-restored-attachment.sh http://127.0.0.1:1820 "$attachment_id" "$attachment_sha256"
unset token
```

在隔离环境演练导入中断，确认重跑安装及运维启动均停止，随后用新数据卷验证正常安装。仓库中的 `bash deploy/kylin-arm64/test-restore-guards.sh` 可单独验证恢复状态及卷身份判断。完整交付验证还包括 Flyway、权限、真实登录和部署压测。
仅构建成功或健康接口通过不能作为完整生产容量验收。

## 第三方来源

- Docker 静态二进制：https://download.docker.com/linux/static/stable/aarch64/docker-29.9.2.tgz
- Compose：https://github.com/docker/compose/releases/download/v2.40.3/docker-compose-linux-aarch64
- 基础镜像：Docker Official Images `mysql:8.4`、`nginx:stable-alpine`、`eclipse-temurin:17-jre-jammy`。
- Docker/Compose 为 Apache-2.0；MySQL Community 为 GPL-2.0，镜像中保留其发行许可证；Nginx、OpenJDK 及系统依赖依各自许可证发行。
- 发行包镜像清单记录实际 ID 和架构，SHA256SUMS 校验全部交付文件；不使用浮动标签重新拉取替换已交付镜像。
