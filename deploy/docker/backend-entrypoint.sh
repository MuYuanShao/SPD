#!/bin/sh
set -eu
# 独立迁移/初始化命令不启动Spring及调度任务。
case "${1:-}" in
  --spd.command=*) exec java -jar /app/app.jar "$@" ;;
esac
if [ "${SPD_REQUIRE_NO_DDL:-false}" = true ]; then
  java -jar /app/app.jar --spd.command=validate
fi
exec java -jar /app/app.jar "$@"
