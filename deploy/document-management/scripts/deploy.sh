#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_env_file
require_command docker
# shellcheck disable=SC1090
set -a; source "${ENV_FILE}"; set +a

"${SCRIPT_DIR}/preflight.sh" | tee "${DEPLOY_DIR}/preflight-$(date +%Y%m%d-%H%M%S).log"

if [[ "${ONLYOFFICE_SKIP_PULL:-false}" == "true" ]]; then
  echo "使用已导入的离线 ONLYOFFICE Docs 镜像……"
  if ! docker image inspect "${ONLYOFFICE_IMAGE}" >/dev/null 2>&1; then
    echo "ERROR: 未找到离线镜像：${ONLYOFFICE_IMAGE}" >&2
    exit 1
  fi
else
  echo "拉取固定 digest 的 ONLYOFFICE Docs 镜像……"
  compose pull
fi

echo "启动独立 DocumentServer 容器……"
compose up -d

deadline=$((SECONDS + 240))
while (( SECONDS < deadline )); do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' rynew-onlyoffice-documentserver 2>/dev/null || true)"
  printf 'health=%s\n' "${health:-starting}"
  [[ "${health}" == "healthy" ]] && break
  [[ "${health}" == "unhealthy" ]] && break
  sleep 5
done

"${SCRIPT_DIR}/verify.sh"
