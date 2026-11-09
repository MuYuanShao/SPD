#!/bin/bash
set -euo pipefail
root=$(cd -- "$(dirname -- "$0")" && pwd)
[[ $EUID == 0 ]] || { echo '请使用 root 或 sudo 运行'; exit 1; }
[[ $(uname -m) == aarch64 ]] || { echo '本包仅支持 ARM64'; exit 1; }
source /etc/os-release
[[ $ID == kylin && $VERSION_ID == V10 ]] || { echo '本包仅支持麒麟 V10'; exit 1; }
for cmd in iptables tar sha256sum systemctl flock curl ss; do command -v "$cmd" >/dev/null || { echo "缺少系统依赖：$cmd"; exit 1; }; done
install_dir=/opt/spd-kylin
unit=/etc/systemd/system/spd-docker.service
if [[ -e $unit ]]; then
  grep -q 'SPD managed offline runtime' "$unit" || { echo '发现非本安装器管理的同名服务'; exit 1; }
fi
mkdir -p "$install_dir/runtime/docker" "$install_dir/runtime/cli-plugins"
if [[ ! -x $install_dir/runtime/docker/dockerd || ! -x $install_dir/runtime/cli-plugins/docker-compose ]]; then
  tar -xzf "$root/vendor/docker-29.9.2.tgz" --strip-components=1 -C "$install_dir/runtime/docker"
  install -m 755 "$root/vendor/docker-compose-linux-aarch64" "$install_dir/runtime/cli-plugins/docker-compose"
fi
daemon_config="$install_dir/runtime/daemon.json"
if [[ ! -e $daemon_config ]]; then
  config_tmp=$(mktemp "$install_dir/runtime/.daemon.XXXXXX")
  printf '{}\n' > "$config_tmp"
  mv -T -- "$config_tmp" "$daemon_config"
fi
unit_tmp=$(mktemp /etc/systemd/system/.spd-docker.XXXXXX)
cat > "$unit_tmp" <<'UNIT'
[Unit]
Description=SPD managed offline runtime
After=network-online.target firewalld.service
Wants=network-online.target
[Service]
Type=notify
Environment=PATH=/opt/spd-kylin/runtime/docker:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ExecStart=/opt/spd-kylin/runtime/docker/dockerd --config-file=/opt/spd-kylin/runtime/daemon.json --host=unix:///run/spd-docker.sock --data-root=/var/lib/spd-docker --exec-root=/run/spd-docker --pidfile=/run/spd-docker.pid --bridge=none
ExecReload=/bin/kill -s HUP $MAINPID
Restart=on-failure
RestartSec=5
TimeoutStartSec=180
LimitNOFILE=infinity
LimitNPROC=infinity
TasksMax=infinity
Delegate=yes
KillMode=process
[Install]
WantedBy=multi-user.target
UNIT
if cmp -s "$unit_tmp" "$unit"; then
  rm -- "$unit_tmp"
else
  chmod 644 "$unit_tmp"
  mv -T -- "$unit_tmp" "$unit"
  systemctl daemon-reload
  systemctl try-restart spd-docker.service
fi
systemctl enable --now spd-docker.service
export DOCKER_HOST=unix:///run/spd-docker.sock
export DOCKER_CONFIG="$install_dir/runtime"
"$install_dir/runtime/docker/docker" info --format '{{.OSType}} {{.Architecture}}'
"$install_dir/runtime/docker/docker" compose version
