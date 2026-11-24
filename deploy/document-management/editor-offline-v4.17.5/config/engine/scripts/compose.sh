#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
docker_local
case "${1:-}" in
  ps|logs|stop|start|restart) compose "$@" ;;
  *) fail 'Allowed commands: ps, logs, stop, start, restart. Use deploy.sh for startup; destructive commands are deliberately not forwarded.' ;;
esac
