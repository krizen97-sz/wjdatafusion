#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.onlyoffice.yml"
ENV_FILE="${DEPLOY_DIR}/.env"

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: 缺少命令：$1" >&2
    exit 1
  fi
}

require_env_file() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "ERROR: ${ENV_FILE} 不存在，请先运行 scripts/prepare-env.sh" >&2
    exit 1
  fi
}
