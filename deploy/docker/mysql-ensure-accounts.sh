#!/bin/sh
set -eu
# Runs after the server is healthy, including when an existing volume skips initdb.d.
for identifier in "$MYSQL_DATABASE" "$MYSQL_USER" "$MYSQL_RUNTIME_USER"; do
  case "$identifier" in ''|*[!a-zA-Z0-9_]*) echo 'Invalid database/account identifier' >&2; exit 1;; esac
done
[ "$MYSQL_USER" != "$MYSQL_RUNTIME_USER" ] && [ "$MYSQL_USER" != root ] && [ "$MYSQL_RUNTIME_USER" != root ] || exit 1
migration_password=$(printf '%s' "$MYSQL_PASSWORD" | sed "s/'/''/g")
runtime_password=$(printf '%s' "$MYSQL_RUNTIME_PASSWORD" | sed "s/'/''/g")
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -hmysql -uroot <<SQL
SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';
CREATE USER IF NOT EXISTS '$MYSQL_USER'@'%' IDENTIFIED BY '$migration_password';
GRANT ALL PRIVILEGES ON \`$MYSQL_DATABASE\`.* TO '$MYSQL_USER'@'%';
CREATE USER IF NOT EXISTS '$MYSQL_RUNTIME_USER'@'%' IDENTIFIED BY '$runtime_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$MYSQL_DATABASE\`.* TO '$MYSQL_RUNTIME_USER'@'%';
SQL
# Existing passwords are intentionally not reset. Wrong .env credentials must fail closed.
