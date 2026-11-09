#!/bin/bash
set -euo pipefail
root=$(cd -- "$(dirname -- "$0")" && pwd)
deploy_root=$(mktemp -d)
cleanup() {
  rm -f -- "$deploy_root"/docker/common.sh "$deploy_root"/state/restore-{started,complete,volumes} \
    "$deploy_root"/data/{ISPD.sql,attachments.tar,data-manifest.json,restore-counts.tsv} \
    "$deploy_root"/{files,restored}/certificate.pdf
  rmdir -- "$deploy_root"/{docker,state,data,files,restored} "$deploy_root"
}
trap cleanup EXIT
mkdir -p "$deploy_root"/{docker,state,data,files,restored}
cp "$root/../docker/common.sh" "$deploy_root/docker/"
source "$root/kylin-common.sh"

expect_failure() {
  if ( "$@" ) >/dev/null 2>&1; then
    printf 'Expected failure from %s\n' "$1" >&2
    exit 1
  fi
}

state=$deploy_root/state
require_restore_retry_safe "$state"
touch "$state/restore-started"
# A failed import may have created zero tables. Its marker alone must block a retry.
expect_failure require_restore_retry_safe "$state"
expect_failure require_restored

export SPD_DB_NAME=ISPD SPD_MYSQL_VOLUME=custom-database-volume
attachments_volume=spd-kylin_spd-application-data
volume_identity() { printf '%s|fixture\n' "$1"; }
printf '%s\n' "$SPD_DB_NAME" "$(volume_identity "$SPD_MYSQL_VOLUME")" \
  "$(volume_identity "$attachments_volume")" > "$state/restore-volumes"
touch "$state/restore-complete"
require_restore_retry_safe "$state"
require_restored
( SPD_MYSQL_VOLUME=replacement-volume; expect_failure require_restored )
( SPD_DB_NAME=other_database; expect_failure require_restored )
( volume_identity() { [[ $1 != "$attachments_volume" ]] && printf '%s|fixture\n' "$1"; }
  expect_failure require_restored )

data=$deploy_root/data
printf 'CREATE TABLE fixture (id INT);\n' > "$data/ISPD.sql"
printf '{}\n' > "$data/data-manifest.json"
printf 'flyway_schema_history\t1\nproduct\t0\nsys_user\t1\nsys_attachment\t0\n' > "$data/restore-counts.tsv"
printf 'attachment fixture\n' > "$deploy_root/files/certificate.pdf"
tar -cf "$data/attachments.tar" -C "$deploy_root/files" .
bash "$root/validate-snapshot.sh" "$data"
tar -xf "$data/attachments.tar" -C "$deploy_root/restored"
tar -df "$data/attachments.tar" -C "$deploy_root/restored"
printf 'corrupted attachment\n' > "$deploy_root/restored/certificate.pdf"
expect_failure tar -df "$data/attachments.tar" -C "$deploy_root/restored"
printf 'USE `ispd`;\n' >> "$data/ISPD.sql"
expect_failure bash "$root/validate-snapshot.sh" "$data"
: > "$data/ISPD.sql"
expect_failure bash "$root/validate-snapshot.sh" "$data"
echo '恢复保护与快照校验测试通过'
