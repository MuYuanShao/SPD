#!/bin/sh
set -eu
# 仅在官方MySQL入口首次初始化空数据卷时运行；不修改已有库或已有账号密码。
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_RUNTIME_USER:?MYSQL_RUNTIME_USER is required}"
: "${MYSQL_RUNTIME_PASSWORD:?MYSQL_RUNTIME_PASSWORD is required}"
for identifier in "$MYSQL_DATABASE" "$MYSQL_RUNTIME_USER"; do
  case "$identifier" in ''|*[!a-zA-Z0-9_]*) echo 'Database/user names must contain only ASCII letters, digits and underscore' >&2; exit 1;; esac
done
if [ "$MYSQL_RUNTIME_USER" = "${MYSQL_USER:-}" ] || [ "$MYSQL_RUNTIME_USER" = root ]; then
  echo 'Migration, runtime and root accounts must be different' >&2; exit 1
fi
# 禁用本SQL会话的反斜线转义，密码按SQL标准双单引号转义；密码不放入命令行或日志。
escaped_password=$(printf '%s' "$MYSQL_RUNTIME_PASSWORD" | sed "s/'/''/g")
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE USER IF NOT EXISTS '$MYSQL_RUNTIME_USER'@'%' IDENTIFIED BY '$escaped_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$MYSQL_DATABASE\`.* TO '$MYSQL_RUNTIME_USER'@'%';
SQL
