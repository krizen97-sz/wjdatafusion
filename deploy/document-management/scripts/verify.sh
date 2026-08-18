#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_env_file
# shellcheck disable=SC1090
set -a; source "${ENV_FILE}"; set +a

CONTAINER_NAME=rynew-onlyoffice-documentserver
GATEWAY_NAME=rynew-onlyoffice-gateway
PUBLIC_URL="http://127.0.0.1:${ONLYOFFICE_PUBLIC_PORT:-8082}"
REPORT="${DEPLOY_DIR}/QA_REPORT.txt"

status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${CONTAINER_NAME}")"
gateway_status="$(docker inspect --format '{{.State.Status}}' "${GATEWAY_NAME}")"
health_body="$(docker exec "${CONTAINER_NAME}" curl -fsS --connect-timeout 3 --max-time 10 http://127.0.0.1/healthcheck)"
api_status="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 10 "${PUBLIC_URL}/web-apps/apps/api/documents/api.js")"
public_health="$(curl -fsS --connect-timeout 3 --max-time 10 "${PUBLIC_URL}/healthcheck")"
backend_status="$(docker exec "${CONTAINER_NAME}" curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 10 "${RUOYI_BACKEND_URL:-http://onlyoffice-gateway:8083}/")"
image_id="$(docker inspect --format '{{.Image}}' "${CONTAINER_NAME}")"
restart_count="$(docker inspect --format '{{.RestartCount}}' "${CONTAINER_NAME}")"
gateway_restart_count="$(docker inspect --format '{{.RestartCount}}' "${GATEWAY_NAME}")"

{
  echo "RYNEW ONLYOFFICE REMOTE QA"
  echo "time=$(date -Iseconds)"
  echo "container_status=${status}"
  echo "gateway_status=${gateway_status}"
  echo "healthcheck=${health_body}"
  echo "public_healthcheck=${public_health}"
  echo "api_js_http=${api_status}"
  echo "backend_callback_path_http=${backend_status}"
  echo "image_id=${image_id}"
  echo "restart_count=${restart_count}"
  echo "gateway_restart_count=${gateway_restart_count}"
  echo "documentserver_public_port=none"
  echo "public_bind=${ONLYOFFICE_PUBLIC_BIND_IP:-0.0.0.0}:${ONLYOFFICE_PUBLIC_PORT:-8082}"
  echo "documentserver_runtime_network_internal=true"
} | tee "${REPORT}"

if [[ "${status}" != "healthy" || "${gateway_status}" != "running" || "${health_body}" != "true" \
  || "${public_health}" != "true" || "${api_status}" != "200" || "${backend_status}" != "200" ]]; then
  echo "ERROR: ONLYOFFICE 基础验收失败；查看 docker logs --tail 200 ${CONTAINER_NAME}" >&2
  exit 1
fi

echo "VERIFY_OK"
