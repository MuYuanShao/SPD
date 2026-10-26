#!/bin/sh
set -eu
deploy_root=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
. "$deploy_root/docker/common.sh"
with_ai=false; no_build=false; init_only=false; wait_timeout=1200; env_file=${SPD_ENV_FILE:-$deploy_root/.env}
while [ "$#" -gt 0 ]; do
  case "$1" in
    --with-ai) with_ai=true;; --no-build) no_build=true;; --init-only) init_only=true;;
    --env-file) shift; env_file=$1;; --wait-timeout) shift; wait_timeout=$1;;
    *) fail "Unknown argument: $1";;
  esac
  shift
done
initialize_env
validate_settings
if [ "$init_only" = true ]; then echo 'Settings validated; no containers started.'; exit 0; fi
require_engine
offline=false
if [ -f "$deploy_root/images.tar" ]; then
  offline=true
  [ -f "$deploy_root/SHA256SUMS" ] || fail 'Offline bundle is missing SHA256SUMS.'
  (cd "$deploy_root" && sha256sum -c SHA256SUMS)
  docker load --input "$deploy_root/images.tar"
  export SPD_OFFLINE=true
  if [ "$with_ai" = true ] && [ -f "$deploy_root/models.tar" ]; then
    project=$(setting COMPOSE_PROJECT_NAME hospital-spd); tag=$(setting SPD_IMAGE_TAG 20261020)
    case "$project" in ''|*[!a-z0-9_-]*) fail 'Invalid Compose project name';; esac
    volume="$project-ollama-models"
    docker volume create "$volume" >/dev/null
    docker run --rm --user 0 --entrypoint /bin/sh --mount "type=volume,src=$volume,dst=/models" --mount "type=bind,src=$deploy_root,dst=/bundle,readonly" "hospital-spd/backend:$tag" -ec 'if [ -z "$(ls -A /models)" ]; then tar -xf /bundle/models.tar -C /models; fi'
  fi
fi
if [ "$offline" = true ]; then compose up -d --wait --wait-timeout "$wait_timeout" --no-build --pull never
elif [ "$no_build" = true ]; then compose up -d --wait --wait-timeout "$wait_timeout" --no-build
else compose up -d --wait --wait-timeout "$wait_timeout" --build; fi
compose exec -T frontend wget -q -O - http://127.0.0.1/api/health
port=$(setting SPD_HTTP_PORT 1820)
printf '\nSPD: http://localhost:%s ; Mobile H5: http://localhost:%s/mobile/\n' "$port" "$port"
printf 'Initial account: admin. Password: SPD_INITIAL_ADMIN_PASSWORD in %s (never printed).\n' "$env_file"
echo 'Reuse .env and the same volumes. Startup never overwrites an administrator-managed password.'
