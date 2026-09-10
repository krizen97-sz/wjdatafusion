set -eu
nonce=$1
work=$2
shift 2
trap 'exit 143' TERM INT
attempt=0
while [ ! -f "$work/.rynew-java-ready" ]; do
  attempt=$((attempt + 1))
  [ "$attempt" -le 600 ] || exit 124
  sleep 0.1
done
[ "$(cat "$work/.rynew-java-ready")" = "$nonce" ] || exit 125
rm "$work/.rynew-java-ready"
if [ -f "$work/control.stop" ]; then
  command=$(cat "$work/control.stop")
  case "$command" in "STOP:$nonce"|"HALT:$nonce") exit 143 ;; esac
fi
exec "$@"
