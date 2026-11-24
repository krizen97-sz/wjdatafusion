#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
verify_payload
docker_local
assert_images
compose config --quiet
# Both Compose service policy and command forbid network pulls, even if an image is absent.
compose up -d --pull never
bash "$OFFICE_ROOT/scripts/verify.sh"
