#!/bin/bash
set -euo pipefail
[[ $# == 3 && $1 =~ ^https?://[a-zA-Z0-9.:-]+$ && $2 =~ ^[1-9][0-9]*$ && $3 =~ ^[a-f0-9]{64}$ ]] ||
  { echo '用法：bash verify-restored-attachment.sh BASE_URL ATTACHMENT_ID SHA256（从标准输入读取访问令牌）' >&2; exit 2; }
IFS= read -r token
[[ $token =~ ^[A-Za-z0-9_.-]+$ ]] || { echo '访问令牌格式不正确' >&2; exit 2; }
checksum=$(printf 'header = "Authorization: Bearer %s"\n' "$token" |
  curl --config - -fsS --max-time 30 "$1/api/licenses/attachments/$2/file" | sha256sum)
[[ ${checksum%% *} == "$3" ]] || { echo '恢复附件的内容与源文件不一致' >&2; exit 1; }
echo '恢复附件的内容校验通过'
