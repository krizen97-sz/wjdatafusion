#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
docker_local
compose stop
printf 'Stopped only the rynew-onlyoffice-offline project; config, images and persistent volumes are retained.\n'
