#!/usr/bin/env bash
set -euo pipefail
OFFICE_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
verify_payload() {
  [[ -s "$OFFICE_ROOT/SHA256SUMS" ]] || fail 'Missing SHA256SUMS; use the complete offline bundle.'
  (cd "$OFFICE_ROOT" && sha256sum --check --quiet SHA256SUMS) || fail 'Bundle checksum verification failed.'
}
load_lock() {
  [[ -s "$OFFICE_ROOT/images.lock.env" ]] || fail 'Missing image lock.'
  # Distributed public metadata, checked by SHA256SUMS before deploy/load.
  set -a
  source "$OFFICE_ROOT/images.lock.env"
  set +a
}
docker_local() {
  [[ "$(uname -s)" == Linux ]] || fail 'Run deployment on Ubuntu/Linux, not macOS/Windows.'
  case "${DOCKER_HOST:-}" in ''|unix://*) ;; *) fail 'Refusing a remote DOCKER_HOST; run on the actual target.' ;; esac
  case "${DOCKER_CONTEXT:-default}" in default|'') ;; *) fail 'Refusing a non-default Docker context.' ;; esac
  command -v docker >/dev/null || fail 'Docker Engine must already be installed.'
  docker info >/dev/null 2>&1 || fail 'Cannot connect to local Docker. Use sudo bash scripts/… if required.'
  [[ "$(docker info --format '{{.OSType}}')" == linux ]] || fail 'A Linux Docker Engine is required.'
  local architecture
  architecture="$(docker info --format '{{.Architecture}}')"
  case "$architecture" in x86_64|amd64) ;; *)
    [[ "${RYNEW_QA_ALLOW_EMULATION:-}" == 1 ]] || fail "This bundle is linux/amd64, but Docker reports $architecture. Do not force-install."
    printf 'WARNING: explicitly enabled QA emulation; not native target acceptance.\n' >&2
  esac
  local version
  version="$(docker version --format '{{.Server.Version}}')"
  [[ "$(printf '%s\n' 20.10 "${version%%-*}" | sort -V | head -n1)" == 20.10 ]] || fail 'Docker Engine 20.10 or newer is required.'
}
assert_images() {
  load_lock
  local ref actual
  for ref in "$ONLYOFFICE_IMAGE" "$GATEWAY_IMAGE"; do
    actual="$(docker image inspect "$ref" --format '{{.Id}} {{.Os}}/{{.Architecture}}' 2>/dev/null)" || fail "Missing image $ref. Run load-images.sh."
    [[ "$actual" == *' linux/amd64' ]] || fail "Image architecture mismatch for $ref."
    image_id_matches "$ref" "${actual%% *}" || fail "Image identity mismatch for $ref. Refusing startup."
  done
}
image_id_matches() {
  local ref="$1" actual="$2" expected imported
  if [[ "$ref" == "$ONLYOFFICE_IMAGE" ]]; then
    expected="$ONLYOFFICE_IMAGE_ID"; imported="$ONLYOFFICE_IMPORTED_MANIFEST_ID"
  else
    expected="$GATEWAY_IMAGE_ID"; imported="$GATEWAY_IMPORTED_MANIFEST_ID"
  fi
  # Classic graphdriver: config digest. Docker 29 containerd store: converted Docker manifest digest.
  [[ "$actual" == "$expected" || "$actual" == "$imported" ]]
}
compose() {
  [[ -s "$OFFICE_ROOT/config/runtime.env" ]] || fail 'Run prepare.sh first.'
  load_lock
  local compose_binary="$OFFICE_ROOT/tools/docker-compose-linux-x86_64"
  [[ -x "$compose_binary" ]] || fail 'Bundled Compose is not executable. Run chmod +x tools/docker-compose-linux-x86_64 on the Linux local disk.'
  "$compose_binary" --project-directory "$OFFICE_ROOT" --env-file "$OFFICE_ROOT/config/runtime.env" \
    -p rynew-onlyoffice-offline -f "$OFFICE_ROOT/compose.yml" "$@"
}
