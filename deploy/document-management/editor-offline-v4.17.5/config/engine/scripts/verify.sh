#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
docker_local
assert_images
office_id="$(compose ps -q onlyoffice-documentserver)"
gateway_id="$(compose ps -q onlyoffice-gateway)"
[[ -n "$office_id" && -n "$gateway_id" ]] || fail 'One or both containers are not running.'
ready=false
for (( attempt=0; attempt<60; attempt++ )); do
  status="$(docker inspect "$office_id" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}')"
  if [[ "$status" == healthy ]]; then ready=true; break; fi
  [[ "$(docker inspect "$office_id" --format '{{.State.Running}}')" == true ]] || fail 'DocumentServer exited; inspect its logs.'
  sleep 5
done
[[ "$ready" == true ]] || fail 'Healthcheck timed out. Check memory, disk, JWT and container logs; do not delete volumes.'
docker exec "$gateway_id" nginx -t
docker exec "$office_id" curl -fsS --max-time 15 http://onlyoffice-gateway:8082/healthcheck
docker exec "$office_id" curl -fsS --max-time 30 -o /dev/null http://onlyoffice-gateway:8082/web-apps/apps/api/documents/api.js
printf '\nPASS: image identity, DocumentServer health, NGINX config and editor API.\n'
printf 'Not yet accepted: real platform JWT, browser editing, callback/save, two-user editing and target firewall. Follow README acceptance checklist.\n'
compose ps
