#!/bin/bash
set -euo pipefail
data=$1
for file in ISPD.sql attachments.tar data-manifest.json restore-counts.tsv; do
  [[ -s $data/$file ]] || { echo "缺少或空的数据文件：$file" >&2; exit 1; }
done
# Standard mysqldump without --databases is portable across schema names.
if LC_ALL=C grep -Eiq '^[[:space:]]*(/\*![0-9]+[[:space:]]*)?(USE[[:space:]]|(CREATE|ALTER|DROP)[[:space:]]+(DATABASE|SCHEMA)[[:space:]])' "$data/ISPD.sql"; then
  echo 'SQL 备份包含切换或创建数据库的语句，请使用不带 --databases 的 mysqldump 重新导出' >&2
  exit 1
fi
awk -F '\t' '
  BEGIN { split("flyway_schema_history product sys_user sys_attachment", names, " ") }
  NF != 2 || $1 != names[NR] || $2 !~ /^(0|[1-9][0-9]*)$/ { exit 1 }
  ($1 == "flyway_schema_history" || $1 == "sys_user") && $2 == 0 { exit 1 }
  END { if (NR != 4) exit 1 }
' "$data/restore-counts.tsv" || { echo '数据库恢复计数清单格式不正确' >&2; exit 1; }
tar -tf "$data/attachments.tar" >/dev/null
