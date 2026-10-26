#!/bin/sh
set -eu
deploy_root=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
. "$deploy_root/docker/common.sh"
with_ai=false; env_file=${SPD_ENV_FILE:-$deploy_root/.env}; output_dir=''
while [ "$#" -gt 0 ]; do
  case "$1" in --with-ai) with_ai=true;; --env-file) shift; env_file=$1;; --output) shift; output_dir=$1;; *) fail "Unknown argument: $1";; esac
  shift
done
initialize_env
validate_settings
require_engine
compose build mysql backend frontend
tag=$(setting SPD_IMAGE_TAG 20261020); project=$(setting COMPOSE_PROJECT_NAME hospital-spd)
if [ "$with_ai" = true ]; then
  compose up -d --wait --wait-timeout 600 ollama
  compose run --rm ollama-init
fi
[ -n "$output_dir" ] || output_dir="$deploy_root/../output/docker-bundle/spd-$tag-$(date +%Y%m%d-%H%M%S)"
[ ! -e "$output_dir" ] || fail 'Output already exists; choose a new directory.'
mkdir -p "$output_dir/docker"
output_dir=$(CDPATH='' cd "$output_dir" && pwd)
for file in start.ps1 start.sh start.bat docker-compose.yml .env.production.example README.docker.md; do cp "$deploy_root/$file" "$output_dir/"; done
cp "$deploy_root/docker/common.ps1" "$deploy_root/docker/common.sh" "$output_dir/docker/"
# Image names are Docker-generated, not arbitrary shell text. Preserve no source/private environment file.
images=$(compose config --images | sort -u)
[ -n "$images" ] || fail 'Cannot resolve image list.'
# Intentional splitting: Compose emits one Docker image reference per line (references cannot contain spaces).
# shellcheck disable=SC2086
docker image save --output "$output_dir/images.tar" $images
if [ "$with_ai" = true ]; then
  docker run --rm --user 0 --entrypoint tar --mount "type=volume,src=$project-ollama-models,dst=/models,readonly" --mount "type=bind,src=$output_dir,dst=/bundle" "hospital-spd/backend:$tag" -cf /bundle/models.tar -C /models .
  compose exec -T ollama ollama show --license "$(setting SPD_AI_MODEL qwen3-vl:8b)" > "$output_dir/model-license.txt"
fi
# shellcheck disable=SC2086
docker image inspect $images --format '{{json .}}' > "$output_dir/images-metadata.jsonl"
(cd "$output_dir" && find . -type f ! -name SHA256SUMS -print | sort | while IFS= read -r file; do sha256sum "${file#./}"; done > SHA256SUMS)
printf 'Complete Docker image bundle: %s\n' "$output_dir"
echo 'Copy the whole directory; run start.bat or sh start.sh. The target needs only Docker/Compose and its OS shell.'
