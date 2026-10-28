# syntax=docker/dockerfile:1
# SPD 全部容器构建入口；宿主机只需要 Docker Engine/Desktop + Compose >= 2.24.4。
# docker compose -f deploy/docker-compose.yml build 构建 mysql / backend / web 三个目标。
# 构建依赖：Maven 3.9.9 + JDK17；Node22/npm（package-lock.json 锁定前端和移动端依赖）。
# 后端依赖由 pom.xml 固定：Spring Boot 3.5.16、Flyway/MySQL驱动、Spring Security/JWT、POI。
# 运行依赖：JRE17、curl健康探针、CA证书、时区、fontconfig/FreeType/中英文字体（Excel/Java2D）。
# Web运行：Nginx，包含管理端 Vue3/Vite/Element Plus 及移动端H5；不需要宿主机Node/npm。
# 数据库：MySQL8.4，初始化独立迁移/运行账号；数据和附件通过Compose命名卷保存。
# 可选AI：Compose ai profile中的Ollama及模型缓存；HIS/手麻/MCP是外部业务系统，不虚构为内置依赖。
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-17
ARG JAVA_IMAGE=eclipse-temurin:17-jre-jammy
ARG NODE_IMAGE=node:22-alpine
ARG NGINX_IMAGE=nginx:stable-alpine
ARG MYSQL_IMAGE=mysql:8.4

FROM ${MAVEN_IMAGE} AS backend-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY backend/src ./src
# 容器构建不连接宿主机测试库；完整测试由发布验证单独执行。
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM ${NODE_IMAGE} AS frontend-build
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY frontend/package.json frontend/package.json
RUN --mount=type=cache,target=/root/.npm npm ci --workspace frontend --include-workspace-root=false
COPY frontend ./frontend
RUN npm --workspace frontend run build

FROM ${NODE_IMAGE} AS mobile-build
WORKDIR /workspace/mobile
COPY mobile/package.json mobile/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY mobile/src ./src
COPY mobile/public ./public
COPY mobile/index.html mobile/tsconfig.json mobile/vite.config.ts ./
# 单独打包H5；Android SDK/Gradle只用于生成APK，不属于服务端运行依赖。
RUN npm run build -- --base=/mobile/

FROM ${NGINX_IMAGE} AS web
RUN apk add --no-cache tzdata
ENV TZ=Asia/Shanghai
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /workspace/frontend/dist /usr/share/nginx/html
COPY --from=mobile-build /workspace/mobile/dist /usr/share/nginx/html/mobile
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1

FROM ${MYSQL_IMAGE} AS mysql
# MySQL官方镜像已包含数据库服务器和mysql客户端，无宿主机数据库依赖。
COPY deploy/docker/mysql-init-runtime.sh /docker-entrypoint-initdb.d/10-spd-runtime.sh
COPY deploy/docker/mysql-ensure-accounts.sh /usr/local/bin/spd-ensure-accounts.sh
COPY deploy/docker/mysql.cnf /etc/mysql/conf.d/spd.cnf
RUN chmod 0444 /etc/mysql/conf.d/spd.cnf && chmod 0555 /docker-entrypoint-initdb.d/10-spd-runtime.sh /usr/local/bin/spd-ensure-accounts.sh
EXPOSE 3306

FROM ${JAVA_IMAGE} AS backend
WORKDIR /app
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       curl ca-certificates tzdata fontconfig libfreetype6 fonts-dejavu-core fonts-noto-cjk tar \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 spd && useradd --uid 10001 --gid spd --home-dir /app --no-create-home spd \
    && mkdir -p /app/output /app/logs && chown -R spd:spd /app
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8 -Djava.awt.headless=true -Duser.timezone=Asia/Shanghai"
COPY --from=backend-build --chown=spd:spd /workspace/backend/target/app.jar app.jar
COPY --chmod=0555 deploy/docker/backend-entrypoint.sh /app/docker-entrypoint.sh
USER spd
EXPOSE 1818
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=12 CMD curl -fsS http://127.0.0.1:1818/api/health || exit 1
ENTRYPOINT ["/app/docker-entrypoint.sh"]
