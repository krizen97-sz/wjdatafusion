#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
verify_payload
docker_local
load_lock
for image_ref in "$ONLYOFFICE_IMAGE" "$GATEWAY_IMAGE"; do
  if docker image inspect "$image_ref" >/dev/null 2>&1; then
    image_id_matches "$image_ref" "$(docker image inspect "$image_ref" --format '{{.Id}}')" || fail "An existing $image_ref points to another image; not overwriting it."
  fi
done
for image_file in "$ONLYOFFICE_ARCHIVE" "$GATEWAY_ARCHIVE"; do
  docker load --input "$OFFICE_ROOT/images/$image_file"
done
# Archives already contain their dedicated local tags; no registry mutation or floating tags.
assert_images
printf 'Both official image IDs and linux/amd64 architecture verified; no registry pull used.\n'
