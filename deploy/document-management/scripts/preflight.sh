#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

TARGET_HOST="${TARGET_HOST:-2.57.0.250}"
PUBLIC_PORT="${ONLYOFFICE_PUBLIC_PORT:-8082}"

echo "[system]"
printf 'hostname=%s\n' "$(hostname)"
printf 'kernel=%s\n' "$(uname -sr)"
printf 'arch=%s\n' "$(uname -m)"
if [[ -r /etc/os-release ]]; then
  . /etc/os-release
  printf 'os=%s\n' "${PRETTY_NAME:-unknown}"
fi
printf 'cpu=%s\n' "$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo unknown)"
if [[ -r /proc/meminfo ]]; then
  awk '/MemTotal/ { printf "memory_kib=%s\n", $2 }' /proc/meminfo
fi
df -Pk / | awk 'NR == 2 { printf "root_free_kib=%s\n", $4 }'

echo "[runtime]"
require_command docker
docker version --format 'docker_server={{.Server.Version}}'
docker compose version
docker info --format 'docker_root={{.DockerRootDir}} storage_driver={{.Driver}}'

echo "[ports]"
if command -v ss >/dev/null 2>&1; then
  for port in 80 443 5554 8080 8081 8082 9000; do
    if ss -lnt "sport = :${port}" 2>/dev/null | tail -n +2 | grep -q .; then
      echo "port_${port}=LISTEN"
    else
      echo "port_${port}=FREE"
    fi
  done
else
  echo "WARN: ss 不可用，无法检查监听端口"
fi

echo "[current_business]"
curl -fsS --connect-timeout 3 --max-time 8 "http://127.0.0.1:8080/" | head -c 240 || true
echo
curl -fsS -o /dev/null -w 'frontend_5554_http=%{http_code}\n' --connect-timeout 3 --max-time 8 "http://127.0.0.1:5554/" || true

echo "[containers]"
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'

echo "[proxy_candidates]"
for path in /etc/nginx/conf.d /etc/nginx/sites-enabled /opt/1panel/apps/openresty/openresty/conf/conf.d /opt/1panel/apps/openresty/openresty/conf/nginx; do
  [[ -d "${path}" ]] && echo "nginx_dir=${path}"
done

echo "[result]"
echo "target=${TARGET_HOST}"
echo "planned_public_port=${PUBLIC_PORT}"

if command -v ss >/dev/null 2>&1 && ss -lnt "sport = :${PUBLIC_PORT}" 2>/dev/null | tail -n +2 | grep -q .; then
  echo "ERROR: 公共测试端口 ${PUBLIC_PORT} 已被占用" >&2
  exit 1
fi

echo "PREFLIGHT_OK"
