# ARM64 交付专用构建入口；context 为准备好的 Linux 构建暂存目录。
FROM mysql:8.4 AS mysql
COPY mysql-init-runtime.sh /docker-entrypoint-initdb.d/10-spd-runtime.sh
COPY mysql-ensure-accounts.sh /usr/local/bin/spd-ensure-accounts.sh
COPY mysql.cnf /etc/mysql/conf.d/spd.cnf
RUN sed -i 's/\r$//' /docker-entrypoint-initdb.d/10-spd-runtime.sh /usr/local/bin/spd-ensure-accounts.sh \
    && chmod 0444 /etc/mysql/conf.d/spd.cnf && chmod 0555 /docker-entrypoint-initdb.d/10-spd-runtime.sh /usr/local/bin/spd-ensure-accounts.sh

FROM nginx:stable-alpine AS web
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY web/ /usr/share/nginx/html/
COPY mobile/ /usr/share/nginx/html/mobile/

FROM eclipse-temurin:17-jre-jammy AS backend
WORKDIR /app
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends curl ca-certificates tzdata fontconfig libfreetype6 fonts-dejavu-core fonts-noto-cjk tar \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 spd && useradd --uid 10001 --gid spd --home-dir /app --no-create-home spd \
    && mkdir -p /app/output /app/logs && chown -R spd:spd /app
ENV TZ=Asia/Shanghai LANG=C.UTF-8 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8 -Djava.awt.headless=true -Duser.timezone=Asia/Shanghai"
COPY --chown=spd:spd app.jar /app/app.jar
COPY backend-entrypoint.sh /app/docker-entrypoint.sh
RUN sed -i 's/\r$//' /app/docker-entrypoint.sh && chmod 0555 /app/docker-entrypoint.sh
USER spd
EXPOSE 1818
ENTRYPOINT ["/app/docker-entrypoint.sh"]
