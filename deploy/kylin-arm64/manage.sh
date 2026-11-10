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
    require_no_pending_upgrade
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
    require_no_pending_upgrade
    backup_deployment
    ;;
  *) echo '用法：bash manage.sh start|stop|restart|status|logs|backup'; exit 2 ;;
esac
