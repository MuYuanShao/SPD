#!/bin/bash
set -euo pipefail
[[ $# == 1 && ! -e $1 ]] || { echo '用法：bash prepare-context.sh 新构建目录' >&2; exit 2; }
repo=$(cd -- "$(dirname -- "$0")/../.." && pwd)
[[ -z $(git -C "$repo" status --porcelain) ]] || {
  echo '请从干净的 Git 提交构建麒麟发行包' >&2
  exit 1
}
revision=$(git -C "$repo" rev-parse HEAD)
out=$1

(cd "$repo" && unset VITE_BASE_PATH && npm ci --workspace frontend --include-workspace-root=false && npm --workspace frontend run build)
(cd "$repo/mobile" && npm ci && npm run build -- --base=/mobile/)
mvn -f "$repo/backend/pom.xml" clean package
[[ $(git -C "$repo" rev-parse HEAD) == "$revision" && -z $(git -C "$repo" status --porcelain) ]] || {
  echo '构建期间源码发生变化，请重新构建' >&2
  exit 1
}

mkdir -- "$out"
cp "$repo/backend/target/app.jar" "$out/"
cp -R "$repo/frontend/dist" "$out/web"
cp -R "$repo/mobile/dist" "$out/mobile"
cp "$repo/deploy/docker/mysql.cnf" "$repo/deploy/nginx.conf" "$out/"
for script in backend-entrypoint.sh mysql-init-runtime.sh mysql-ensure-accounts.sh; do
  sed 's/\r$//' "$repo/deploy/docker/$script" > "$out/$script"
done
printf '%s\n' "$revision" > "$out/source-revision"
echo "构建 context：$out"
