#!/bin/sh
# Shared POSIX utilities. No Node, Python, Maven, Java or host MySQL is required.
fail() { printf '%s\n' "$*" >&2; exit 1; }
setting() {
  value=$(printenv "$1" 2>/dev/null || true)
  if [ -z "$value" ] && [ -f "$env_file" ]; then value=$(sed -n "s/^$1=//p" "$env_file" | head -n 1 | tr -d '\r'); fi
  case "$value" in \"*\") value=${value#\"}; value=${value%\"};; \'*\') value=${value#\'}; value=${value%\'};; esac
  printf '%s' "${value:-$2}"
}
secret() { od -An -N "$1" -tx1 /dev/urandom | tr -d ' \n'; }
initialize_env() {
  [ ! -f "$env_file" ] || return 0
  ( umask 077; set -C; {
    printf '%s\n' '# Generated once; keep private and reuse with the same data volumes.'
    printf '%s\n' 'COMPOSE_PROJECT_NAME=hospital-spd' 'SPD_IMAGE_TAG=20261020' 'SPD_HTTP_PORT=1820' 'SPD_DB_NAME=ISPD' 'SPD_DB_USERNAME=spd_runtime'
    printf 'SPD_DB_PASSWORD=%s\nSPD_DB_ROOT_PASSWORD=%s\nSPD_FLYWAY_USERNAME=spd_migrate\nSPD_FLYWAY_PASSWORD=%s\n' "$(secret 24)" "$(secret 24)" "$(secret 24)"
    printf 'SPD_JWT_SECRET=%s\nSPD_INITIAL_ADMIN_PASSWORD=%s\n' "$(secret 48)" "$(secret 16)"
    printf 'SPD_MOBILE_ENABLED=true\nSPD_MOBILE_SERVER_ID=%s\nSPD_MOBILE_REVIEW_SECRET=%s\n' "$(secret 16)" "$(secret 32)"
    printf '%s\n' 'SPD_AI_MODEL=qwen3-vl:8b' 'SPD_AI_BASE_URL=http://ollama:11434' 'SPD_MCP_ENABLED=false' 'SPD_MCP_UPLOAD_ENABLED=false'
  } > "$env_file" ) || fail 'Cannot create a private deployment environment file.'
  printf 'Private settings created: %s\n' "$env_file"
}
compose() {
  if [ "$with_ai" = true ]; then docker compose --env-file "$env_file" -f "$deploy_root/docker-compose.yml" --profile ai "$@"
  else docker compose --env-file "$env_file" -f "$deploy_root/docker-compose.yml" "$@"; fi
}
validate_settings() {
  command -v docker >/dev/null 2>&1 || fail 'Install Docker Engine/Desktop with Compose >= 2.24.4 first.'
  compose config --quiet
  for key in SPD_DB_PASSWORD SPD_DB_ROOT_PASSWORD SPD_FLYWAY_PASSWORD SPD_INITIAL_ADMIN_PASSWORD SPD_JWT_SECRET; do
    value=$(setting "$key" '')
    min=16; [ "$key" != SPD_JWT_SECRET ] || min=48
    [ "${#value}" -ge "$min" ] || fail "$key is missing or too short. Update the private .env file."
    case "$value" in *replace*|*change-me*|admin123|root123|password) fail "$key contains an unsafe placeholder";; esac
  done
  [ "$(setting SPD_DB_USERNAME spd_runtime)" != "$(setting SPD_FLYWAY_USERNAME spd_migrate)" ] || fail 'Use separate migration/runtime accounts.'
  [ "$(setting SPD_DB_USERNAME spd_runtime)" != root ] || fail 'Runtime must not use root.'
  [ "$(setting SPD_FLYWAY_USERNAME spd_migrate)" != root ] || fail 'Migration must not use root.'
  runtime_password=$(setting SPD_DB_PASSWORD ''); migration_password=$(setting SPD_FLYWAY_PASSWORD ''); root_password=$(setting SPD_DB_ROOT_PASSWORD '')
  [ "$runtime_password" != "$migration_password" ] || fail 'Runtime and migration passwords must be different.'
  admin_bytes=$(printf '%s' "$(setting SPD_INITIAL_ADMIN_PASSWORD '')" | wc -c | tr -d ' ')
  [ "$admin_bytes" -le 72 ] || fail 'Initial administrator password must not exceed 72 UTF-8 bytes.'
}
require_engine() {
  kind=$(docker info --format '{{.OSType}}' 2>/dev/null) || fail 'Docker Linux engine is not ready. Start Docker Desktop/Engine and retry.'
  [ "$kind" = linux ] || fail 'Switch Docker Desktop to Linux containers.'
}

check_existing_database() {
  docker info --format '{{.OSType}}' >/dev/null 2>&1 || return 0
  project=$(setting COMPOSE_PROJECT_NAME hospital-spd)
  expected=$(setting SPD_MYSQL_VOLUME "${project}_spd-mysql-data")
  volumes=$(docker volume ls --filter label=com.docker.compose.volume=spd-mysql-data --format '{{.Name}}') || fail 'Cannot inspect database volumes'
  if docker volume inspect "$expected" >/dev/null 2>&1 && [ ! -f "$env_file" ]; then
    fail 'Database volume already exists. Restore its original private environment file instead of generating replacement credentials.'
  fi
  if ! docker volume inspect "$expected" >/dev/null 2>&1 && [ -n "$volumes" ] && [ "$(setting SPD_ALLOW_NEW_DATABASE false)" != true ]; then
    fail 'Existing SPD database volumes detected. Preserve SPD_MYSQL_VOLUME and use deploy/upgrade-legacy-env.sh. No database was created.'
  fi
}
