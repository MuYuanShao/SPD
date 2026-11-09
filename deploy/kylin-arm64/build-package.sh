#!/bin/bash
# 在 ARM64 构建机上运行。数据库、附件和二进制须由授权的导出过程提供。
set -euo pipefail
umask 077
[[ $# == 4 ]] || { echo '用法：bash build-package.sh CONTEXT DATA VENDOR OUTPUT'; exit 2; }
root=$(cd -- "$(dirname -- "$0")" && pwd)
context=$(cd "$1" && pwd); data=$(cd "$2" && pwd); vendor=$(cd "$3" && pwd)
out=$4
[[ $(uname -m) == aarch64 ]] || { echo '请在 ARM64 构建机运行'; exit 1; }
[[ ! -e $out ]] || { echo '输出目录已存在，禁止覆盖'; exit 1; }
bash "$root/validate-snapshot.sh" "$data"
for file in docker-29.9.2.tgz docker-compose-linux-aarch64; do [[ -s $vendor/$file ]] || { echo "缺少运行环境：$file"; exit 1; }; done
revision=$(cat "$context/source-revision")
[[ $revision =~ ^([0-9a-f]{40}|[0-9a-f]{64})$ ]] || { echo '构建 context 须包含完整 Git 提交 SHA（source-revision）'; exit 1; }
build_tag="build-$(date +%s)-$$"
targets=(mysql backend web)
build_images=()
cleanup_build_tags() {
  if (( ${#build_images[@]} )); then docker image rm "${build_images[@]}" >/dev/null || true; fi
}
trap cleanup_build_tags EXIT
for target in "${targets[@]}"; do
  image="spd-kylin/$target:$build_tag"
  DOCKER_BUILDKIT=0 docker build --network host --build-arg http_proxy --build-arg https_proxy \
    -f - --target "$target" -t "$image" "$context" < "$root/Dockerfile"
  build_images+=("$image")
  [[ $(docker image inspect --format '{{.Architecture}}' "$image") == arm64 ]] || { echo '镜像架构错误'; exit 1; }
done
image_digest=$(docker image inspect --format '{{.Id}}' "${build_images[@]}" | sha256sum)
tag="${revision:0:12}-${image_digest:0:24}"
images=()
for target in "${targets[@]}"; do
  image="spd-kylin/$target:$tag"
  docker image tag "spd-kylin/$target:$build_tag" "$image"
  images+=("$image")
done
mkdir -p "$out/docker" "$out/data" "$out/vendor"
cp "$root/"{install.sh,install-runtime.sh,manage.sh,kylin-common.sh,validate-snapshot.sh,verify-restored-attachment.sh,docker-compose.yml,README.md} "$out/"
cp "$root/../docker/common.sh" "$out/docker/"
cp "$vendor/"{docker-29.9.2.tgz,docker-compose-linux-aarch64} "$out/vendor/"
cp "$data/"{ISPD.sql,attachments.tar,data-manifest.json,restore-counts.tsv} "$out/data/"
docker save -o "$out/images.tar" "${images[@]}"
docker image inspect "${images[@]}" > "$out/images-metadata.json"
printf '{"platform":"kylin-v10-arm64","baseCommit":"%s","imageTag":"%s","aiIncluded":false,"includesBusinessSnapshot":true}\n' "$revision" "$tag" > "$out/release-manifest.json"
printf '%s\n' "$tag" > "$out/image-tag"
(cd "$out" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
tar -czf "$out.tar.gz" -C "$(dirname "$out")" "$(basename "$out")"
(cd "$(dirname "$out")" && sha256sum "$(basename "$out").tar.gz" > "$(basename "$out").tar.gz.sha256")
echo "独立麒麟 ARM64 安装包：$out.tar.gz"
