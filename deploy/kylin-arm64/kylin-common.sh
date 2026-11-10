#!/bin/bash
# Sourced by the installer and management commands.
source "$deploy_root/docker/common.sh"

lock_deployment() {
  exec 9>/run/spd-kylin-install.lock
  flock -n 9 || fail '另一个安装或运维任务正在运行'
}

load_deployment_settings() {
  COMPOSE_PROJECT_NAME=$(setting COMPOSE_PROJECT_NAME spd-kylin)
  [[ $COMPOSE_PROJECT_NAME == spd-kylin ]] || fail '部署标识不匹配'
  SPD_MYSQL_VOLUME=$(setting SPD_MYSQL_VOLUME spd-kylin_spd-mysql-data)
  SPD_HTTP_PORT=$(setting SPD_HTTP_PORT 1820)
  SPD_DB_NAME=$(setting SPD_DB_NAME ISPD)
  SPD_IMAGE_TAG=$(setting SPD_IMAGE_TAG '')
  [[ $SPD_MYSQL_VOLUME =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]+$ ]] || fail '数据库卷名不合法'
  [[ $SPD_DB_NAME =~ ^[a-zA-Z0-9_]+$ ]] || fail '数据库名不合法'
  [[ $SPD_IMAGE_TAG =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]*$ ]] || fail '镜像标签不合法'
  [[ $SPD_HTTP_PORT =~ ^[1-9][0-9]{0,4}$ ]] && (( SPD_HTTP_PORT <= 65535 )) || fail 'Web 端口不合法'
  export COMPOSE_PROJECT_NAME SPD_MYSQL_VOLUME SPD_HTTP_PORT SPD_DB_NAME SPD_IMAGE_TAG
  attachments_volume=spd-kylin_spd-application-data
}

volume_identity() {
  docker volume inspect --format '{{.Name}}|{{.CreatedAt}}' "$1"
}

require_restore_retry_safe() {
  [[ ! -f $1/restore-started || -f $1/restore-complete ]] ||
    fail '上次恢复未完成，已停止自动重试，请检查数据库及安装日志'
}

require_restored() {
  [[ -f $deploy_root/state/restore-complete && -f $deploy_root/state/restore-volumes ]] ||
    fail '数据恢复尚未完成或缺少卷身份记录，请检查原库、附件及安装日志'
  local database_id attachments_id
  database_id=$(volume_identity "$SPD_MYSQL_VOLUME") || fail '原数据库卷不存在'
  attachments_id=$(volume_identity "$attachments_volume") || fail '原附件卷不存在'
  printf '%s\n' "$SPD_DB_NAME" "$database_id" "$attachments_id" |
    cmp -s - "$deploy_root/state/restore-volumes" || fail '数据库或附件卷身份变化，请恢复原卷及配置'
}

mysql_root() {
  compose exec -T mysql sh -ec '
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --database="$MYSQL_DATABASE" "$@"
  ' mysql "$@"
}

restore_counts() {
  mysql_root --batch --raw --skip-column-names -e "
    SELECT 'flyway_schema_history', COUNT(*) FROM flyway_schema_history;
    SELECT 'product', COUNT(*) FROM product;
    SELECT 'sys_user', COUNT(*) FROM sys_user;
    SELECT 'sys_attachment', COUNT(*) FROM sys_attachment;"
}

check_web_health() {
  local binding
  binding=$(compose port frontend 80)
  binding=${binding%%$'\n'*}
  [[ ${binding##*:} =~ ^[0-9]+$ ]] || fail '无法解析实际 Web 端口'
  curl -fsS --noproxy '*' "http://127.0.0.1:${binding##*:}/api/health"
}

require_no_pending_upgrade() {
  [[ ! -f $deploy_root/state/upgrade-pending ]] ||
    fail "存在未完成的升级，请用同一发行包重新运行 install.sh --upgrade"
}

require_upgrade_retry_safe() {
  [[ ! -f $1 || $(cat "$1") == "$2" ]] ||
    fail '存在其他发行包的未完成升级，请先用原升级包恢复'
}

backup_deployment() (
  local keep_stopped=${1:-false}
  umask 077
  mkdir -p "$deploy_root/backups"
  staging=$(mktemp -d "$deploy_root/backups/.partial.XXXXXXXX")
  backup="$deploy_root/backups/$(date +%Y%m%d-%H%M%S)-${staging##*.}"
  running=$(compose ps --status running --services backend frontend)
  paused=()
  for service in backend frontend; do
    if grep -Fxq "$service" <<< "$running"; then paused+=("$service"); fi
  done
  finish_backup() {
    local result=$?
    if (( ${#paused[@]} )); then
      compose start --wait --wait-timeout 300 "${paused[@]}" || result=1
    fi
    if (( result )); then printf '备份未完成，临时目录：%s\n' "$staging" >&2; fi
    exit "$result"
  }
  trap finish_backup EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  if (( ${#paused[@]} )); then compose stop "${paused[@]}"; fi
  compose exec -T mysql sh -ec '
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --events --hex-blob --set-gtid-purged=OFF "$MYSQL_DATABASE"
  ' > "$staging/ISPD.sql"
  restore_counts > "$staging/restore-counts.tsv"
  printf '{"database":"%s","exportedAt":"%s","imageTag":"%s"}\n' \
    "$SPD_DB_NAME" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$SPD_IMAGE_TAG" > "$staging/data-manifest.json"
  docker run --rm --network none --user 0 --entrypoint tar \
    -v "$attachments_volume:/data:ro" -v "$staging:/backup" "spd-kylin/backend:$SPD_IMAGE_TAG" -cf /backup/attachments.tar -C /data .
  cp "$env_file" "$staging/private.env"
  (cd "$staging" && sha256sum ISPD.sql restore-counts.tsv attachments.tar data-manifest.json private.env > SHA256SUMS)
  if [[ $keep_stopped != true ]] && (( ${#paused[@]} )); then
    compose start --wait --wait-timeout 300 "${paused[@]}"
    paused=()
  fi
  mv -T -- "$staging" "$backup"
  trap - EXIT INT TERM
  printf '备份已保存：%s（包含私密配置，请妥善保管）\n' "$backup"
)
