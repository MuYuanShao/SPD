#!/bin/bash
set -euo pipefail
root=$(cd -- "$(dirname -- "$0")" && pwd)
[[ $EUID == 0 ]] || { echo '请使用 root 或 sudo 运行'; exit 1; }
umask 077
app=/opt/spd-kylin/app
deploy_root=$root
env_file="$app/.env"
with_ai=false
source "$root/kylin-common.sh"
lock_deployment
[[ -s $root/SHA256SUMS && -s $root/images.tar ]] || fail '安装包不完整：缺少校验清单或运行镜像'
(cd "$root" && sha256sum -c SHA256SUMS)
bash "$root/validate-snapshot.sh" "$root/data"
package_tag=$(cat "$root/image-tag")
[[ $package_tag =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]*$ ]] || fail '安装包镜像标签不合法'
require_restore_retry_safe "$app/state"
if [[ -f $app/state/restore-complete ]]; then
  [[ -f $env_file && -f $app/state/restore-volumes ]] ||
    fail '缺少原 .env 或卷身份记录，请在升级前核对原数据库卷和附件卷'
fi
if [[ -f $env_file ]]; then
  load_deployment_settings
  [[ $SPD_IMAGE_TAG == "$package_tag" ]] || fail '安装包与当前配置的镜像标签不同，请使用匹配的发行包'
fi

port=$(setting SPD_HTTP_PORT 1820)
[[ $port =~ ^[1-9][0-9]{0,4}$ ]] && (( port <= 65535 )) || fail 'Web 端口不合法'
if [[ ! -f $app/state/restore-complete ]] && [[ -n $(ss -lntH "sport = :$port") ]]; then
  fail "$port 端口已被占用，已停止首次安装"
fi
firewall_zone=$(setting SPD_FIREWALL_ZONE '')
if [[ -n $firewall_zone ]]; then
  [[ $firewall_zone =~ ^[a-zA-Z0-9_-]+$ ]] || fail '防火墙区域名称不合法'
  systemctl is-active --quiet firewalld || fail '指定防火墙区域时 firewalld 必须运行'
  firewall-cmd --zone="$firewall_zone" --list-ports >/dev/null
fi

bash "$root/install-runtime.sh"
export PATH=/opt/spd-kylin/runtime/docker:$PATH
export DOCKER_HOST=unix:///run/spd-docker.sock DOCKER_CONFIG=/opt/spd-kylin/runtime
unset DOCKER_CONTEXT
mkdir -p "$app/docker" "$app/state"
if [[ ! -f $env_file ]]; then
  export SPD_IMAGE_TAG="$package_tag"
  load_deployment_settings
  [[ ! -f $app/state/restore-complete ]] || fail '缺少原 .env，请先恢复原私有配置'
  existing_volumes=$(docker volume ls --filter label=com.docker.compose.project=spd-kylin --filter label=com.docker.compose.volume=spd-mysql-data -q)
  if docker volume inspect "$SPD_MYSQL_VOLUME" >/dev/null 2>&1 ||
     docker volume inspect spd-kylin_spd-mysql-data >/dev/null 2>&1 ||
     [[ -n $existing_volumes ]]; then
    fail '已有数据库卷，必须恢复原 .env'
  fi
  init_dir=$(mktemp -d "$app/.init.XXXXXX")
  trap 'rm -f -- "$init_dir/.env"; rmdir -- "$init_dir"' EXIT
  env_file="$init_dir/.env"
  initialize_env
  sed -i -e 's/^COMPOSE_PROJECT_NAME=.*/COMPOSE_PROJECT_NAME=spd-kylin/' \
    -e "s/^SPD_IMAGE_TAG=.*/SPD_IMAGE_TAG=$SPD_IMAGE_TAG/" \
    -e "s/^SPD_DB_NAME=.*/SPD_DB_NAME=$SPD_DB_NAME/" \
    -e "s/^SPD_HTTP_PORT=.*/SPD_HTTP_PORT=$SPD_HTTP_PORT/" \
    -e 's/^SPD_MOBILE_ENABLED=.*/SPD_MOBILE_ENABLED=false/' "$env_file"
  printf 'SPD_MYSQL_VOLUME=%s\nSPD_FIREWALL_ZONE=%s\n' "$SPD_MYSQL_VOLUME" "$firewall_zone" >> "$env_file"
  mv -- "$env_file" "$app/.env"
  env_file="$app/.env"
fi
load_deployment_settings
[[ $SPD_IMAGE_TAG == "$package_tag" ]] || fail '安装包与当前配置的镜像标签不同，请使用匹配的发行包'
validate_settings
if [[ -f $app/state/restore-complete ]]; then
  deploy_root=$app require_restored
fi

docker load -i "$root/images.tar"
for image in mysql backend web; do
  [[ $(docker image inspect --format '{{.Architecture}}' "spd-kylin/$image:$SPD_IMAGE_TAG") == arm64 ]] ||
    fail "镜像架构错误：$image"
done

compose up -d --wait --wait-timeout 300 --no-build --pull never mysql
if [[ ! -f $app/state/restore-complete ]]; then
  tables=$(mysql_root --batch --skip-column-names -e 'SELECT COUNT(*) FROM information_schema.tables WHERE BINARY table_schema = BINARY DATABASE()')
  [[ $tables == 0 ]] || fail '目标数据库非空，禁止覆盖导入'
  touch "$app/state/restore-started"
  mysql_root < "$root/data/ISPD.sql"
  restore_counts > "$app/state/restored-counts.tsv"
  cmp -s "$root/data/restore-counts.tsv" "$app/state/restored-counts.tsv" ||
    fail '目标数据库的实际数据与备份清单不符'
  docker volume create --label com.docker.compose.project=spd-kylin \
    --label com.docker.compose.volume=spd-application-data "$attachments_volume" >/dev/null
  docker run --rm --network none --user 0 --entrypoint sh \
    -v "$attachments_volume:/data" -v "$root/data:/backup:ro" "spd-kylin/backend:$SPD_IMAGE_TAG" -ec '
    test -z "$(ls -A /data)"
    tar -xf /backup/attachments.tar -C /data
    tar -df /backup/attachments.tar -C /data
    chown -R 10001:10001 /data
  '
  cp "$root/data/data-manifest.json" "$app/state/data-manifest.json"
  database_id=$(volume_identity "$SPD_MYSQL_VOLUME")
  attachments_id=$(volume_identity "$attachments_volume")
  printf '%s\n' "$SPD_DB_NAME" "$database_id" "$attachments_id" > "$app/state/restore-volumes"
  touch "$app/state/restore-complete"
fi

for file in docker-compose.yml kylin-common.sh manage.sh; do cp "$root/$file" "$app/$file"; done
cp "$root/docker/common.sh" "$app/docker/common.sh"
deploy_root=$app
compose up -d --wait --wait-timeout 600 --no-build --pull never
check_web_health
if [[ -n $firewall_zone ]]; then
  firewall-cmd --zone="$firewall_zone" --permanent --add-port="$SPD_HTTP_PORT/tcp"
  firewall-cmd --zone="$firewall_zone" --add-port="$SPD_HTTP_PORT/tcp"
fi
printf '\n安装成功。访问：http://服务器IP:%s\n运维：bash %s/manage.sh status\n私有配置：%s（请勿公开）\n' "$SPD_HTTP_PORT" "$app" "$env_file"
