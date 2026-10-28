#!/bin/sh
set -eu
[ "$#" -ge 2 ] || { echo 'Usage: sh upgrade-legacy-env.sh LEGACY_ENV EXISTING_VOLUME [OUTPUT_ENV]' >&2; exit 1; }
deploy_root=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
. "$deploy_root/docker/common.sh"
env_file=$1
volume=$2
output=${3:-$deploy_root/.env.upgraded}
[ ! -e "$output" ] || fail 'Output environment already exists; choose a new file'
user=$(setting SPD_DB_USERNAME '')
password=$(setting SPD_DB_PASSWORD '')
[ -n "$user" ] && [ -n "$password" ] || fail 'Original database credentials are required'
[ "$user" != root ] || fail 'Use a dedicated schema owner instead of root'
docker volume inspect "$volume" >/dev/null || fail 'Original volume does not exist'
runtime=spd_runtime
[ "$user" != spd_runtime ] || runtime=spd_app
# Preserve all original settings; replace only deployment identity/runtime keys.
(umask 077; set -C; {
  sed '/^SPD_FLYWAY_USERNAME=/d; /^SPD_FLYWAY_PASSWORD=/d; /^SPD_DB_USERNAME=/d; /^SPD_DB_PASSWORD=/d; /^SPD_MYSQL_VOLUME=/d; /^COMPOSE_PROJECT_NAME=/d' "$env_file"
  printf '\nCOMPOSE_PROJECT_NAME=%s\nSPD_MYSQL_VOLUME=%s\n' "$(setting COMPOSE_PROJECT_NAME deploy)" "$volume"
  printf "SPD_FLYWAY_USERNAME='%s'\nSPD_FLYWAY_PASSWORD='%s'\n" "$user" "$(printf '%s' "$password" | sed "s/'/\\\\'/g")"
  printf 'SPD_DB_USERNAME=%s\nSPD_DB_PASSWORD=%s\n' "$runtime" "$(secret 24)"
  [ -n "$(setting SPD_INITIAL_ADMIN_PASSWORD '')" ] || printf 'SPD_INITIAL_ADMIN_PASSWORD=%s\n' "$(secret 16)"
} > "$output") || fail 'Cannot create protected upgrade environment'
echo 'Upgrade environment prepared; original configuration and volume unchanged. Back up before starting with --env-file.'
