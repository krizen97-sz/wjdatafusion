#!/usr/bin/env bash
# First installation on a host with a platform and a local Docker Engine.
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
if (( $# < 1 || $# > 3 )); then
  printf '%s\n' 'Usage: bash scripts/prepare.sh browser-lan-host [backend-host] [backend-port]' >&2
  exit 2
fi
browser_host=$1
backend_host=${2:-host.docker.internal}
backend_port=${3:-8080}
for host in "$browser_host" "$backend_host"; do
  [[ "$host" =~ ^[[:alnum:]][[:alnum:].-]*$ ]] || fail 'Use an IPv4 address or DNS hostname without scheme, port or path.'
done
[[ "$backend_port" =~ ^[0-9]{1,5}$ ]] && (( 10#$backend_port >= 1 && 10#$backend_port <= 65535 )) || fail 'Invalid backend TCP port.'
[[ ! -e "$OFFICE_ROOT/config" && ! -L "$OFFICE_ROOT/config" ]] || fail 'Existing config detected. Reuse its secret and settings.'
umask 077
mkdir "$OFFICE_ROOT/config"
secret=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
[[ "$secret" =~ ^[0-9a-f]{64}$ ]] || fail 'Unable to generate a secure signing secret.'
printf '%s\n' "ONLYOFFICE_JWT_SECRET=$secret" 'PUBLIC_BIND_IP=127.0.0.1' 'PUBLIC_PORT=8082' > "$OFFICE_ROOT/config/runtime.env"
sed -e "s/__BACKEND_HOST__/$backend_host/g" -e "s/__BACKEND_PORT__/$backend_port/g" \
  "$OFFICE_ROOT/nginx.conf.template" > "$OFFICE_ROOT/config/nginx.conf"
printf '%s\n' \
  'DOCUMENT_MANAGEMENT_ONLY_OFFICE_ENABLED=true' \
  'DOCUMENT_MANAGEMENT_ONLY_OFFICE_BROWSER_PATH=/onlyoffice' \
  'DOCUMENT_MANAGEMENT_ONLY_OFFICE_SERVER_URL=http://127.0.0.1:8082' \
  'DOCUMENT_MANAGEMENT_ONLY_OFFICE_PLATFORM_BASE_URL=http://onlyoffice-gateway:8083' \
  "DOCUMENT_MANAGEMENT_ONLY_OFFICE_JWT_SECRET=$secret" \
  "DOCUMENT_MANAGEMENT_ONLY_OFFICE_TRUSTED_DOWNLOAD_HOSTS=$browser_host,onlyoffice-documentserver,onlyoffice-gateway" \
  'DOCUMENT_MANAGEMENT_ONLY_OFFICE_LANGUAGE=zh-CN' \
  'DOCUMENT_MANAGEMENT_ONLY_OFFICE_REGION=zh-CN' \
  'DOCUMENT_MANAGEMENT_ONLY_OFFICE_PLUGINS_ENABLED=false' > "$OFFICE_ROOT/config/backend-document.env"
unset secret
printf '%s\n' 'Generated runtime.env, nginx.conf and backend-document.env in the private config/ directory.'
