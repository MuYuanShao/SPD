#!/bin/bash
set -euo pipefail
deploy_root=$(cd -- "$(dirname -- "$0")" && pwd)
env_file="$deploy_root/.env"
with_ai=false
source "$deploy_root/kylin-common.sh"
export PATH=/opt/spd-kylin/runtime/docker:$PATH
export DOCKER_HOST=unix:///run/spd-docker.sock DOCKER_CONFIG=/opt/spd-kylin/runtime
unset DOCKER_CONTEXT
[[ -f $env_file ]] || fail '请先运行 install.sh'
load_deployment_settings
case ${1:-status} in
  start|restart)
    lock_deployment
    require_restored
    if [[ $1 == restart ]]; then compose stop frontend backend; fi
    compose up -d --wait --wait-timeout 600 --no-build --pull never
    check_web_health
    ;;
  stop)
    lock_deployment
    compose stop
    ;;
  status) compose ps -a ;;
  logs) compose logs --tail=100 mysql migrate bootstrap backend frontend ;;
  backup)
    lock_deployment
    require_restored
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
    if (( ${#paused[@]} )); then compose stop "${paused[@]}"; fi
    compose exec -T mysql sh -ec '
      MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --events --hex-blob --set-gtid-purged=OFF "$MYSQL_DATABASE"
    ' > "$staging/ISPD.sql"
    restore_counts > "$staging/restore-counts.tsv"
    docker run --rm --network none --user 0 --entrypoint tar \
      -v "$attachments_volume:/data:ro" -v "$staging:/backup" "spd-kylin/backend:$SPD_IMAGE_TAG" -cf /backup/attachments.tar -C /data .
    cp "$env_file" "$staging/private.env"
    (cd "$staging" && sha256sum ISPD.sql restore-counts.tsv attachments.tar private.env > SHA256SUMS)
    if (( ${#paused[@]} )); then
      compose start --wait --wait-timeout 300 "${paused[@]}"
      paused=()
    fi
    mv -T -- "$staging" "$backup"
    trap - EXIT
    printf '备份已保存：%s（包含私密配置，请妥善保管）\n' "$backup"
    ;;
  *) echo '用法：bash manage.sh start|stop|restart|status|logs|backup'; exit 2 ;;
esac
