#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_env_file
# shellcheck disable=SC1090
set -a; source "${ENV_FILE}"; set +a

require_command docker
require_command gzip
mkdir -p "${DEPLOY_DIR}/artifacts"

stamp="$(date +%Y%m%d-%H%M%S)"
archive="${DEPLOY_DIR}/artifacts/onlyoffice-documentserver-9.4.0-${stamp}.tar.gz"
echo "在远程服务器导出固定镜像，目标：${archive}"
docker image save "${ONLYOFFICE_IMAGE}" | gzip -1 > "${archive}"
sha256sum "${archive}" > "${archive}.sha256"
ls -lh "${archive}" "${archive}.sha256"
