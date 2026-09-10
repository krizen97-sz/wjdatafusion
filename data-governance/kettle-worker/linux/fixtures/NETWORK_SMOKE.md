# Bounded original-engine Linux network acceptance

This fixture is only for the reviewed rootful Linux Docker host. The authoring task ran Python mocks, real local file permission checks and JavaScript syntax checks; it did not execute Docker, iptables, nsenter or any network listener. Root must review and execute the live test separately.

Make a separate mode-600 controller config with the existing reviewed image ID/artifacts and these exact values:

```json
{
  "execution_enabled": true,
  "stage_run_owner": true,
  "endpoints": [{"host": "172.17.0.1", "port": 39090}]
}
```

These are additions to a complete existing config, not a standalone config. The script rejects an empty allowlist, extra endpoints, or a different host/port. The XML cannot add permissions. The existing `uid`/`gid` remain nonroot; the controller must be root for private namespace rules. A read-only `ip -j -4 addr show dev docker0` must confirm `172.17.0.1` belongs to docker0. It does not substitute another address.

Commands from the reviewed source root:

```sh
# Creates a fresh private synthetic operation and prints the plan only; no listener or Docker call.
python3 data-governance/kettle-worker/linux/network_smoke.py --config /private/reviewed-network-smoke.json

# Only after root review. Uses another fresh run; does not replay the plan's run ID.
python3 data-governance/kettle-worker/linux/network_smoke.py --config /private/reviewed-network-smoke.json --execute
```

The two foreground TCP listeners bind only `172.17.0.1:39090` and `172.17.0.1:39091`. No wildcard/public-interface bind, port reuse, daemon, subprocess fixture or background persistent service is used. An occupied port aborts without touching its owner. Both listeners close in `finally` and their threads are joined.

The original engine runs exactly one RowGenerator row through ScriptValueMod and TextFileOutput. The script uses original Rhino Java interop and bounded Java Socket/DatagramSocket calls to perform four probes:

| Probe | Actual target | Required evidence |
| --- | --- | --- |
| Allowed TCP | docker0:39090 | Exactly one accepted socket and a nonce roundtrip |
| Denied TCP | docker0:39091 | Native connect timeout/error and zero accepted sockets |
| Docker DNS TCP | 127.0.0.11:53 | Native connect timeout/error and a positive dedicated kernel DROP counter delta |
| Docker DNS UDP | 127.0.0.11:53 | No response, native timeout/error, and a positive dedicated kernel DROP counter delta; successful send alone proves nothing |

The UDP payload is inert synthetic bytes, not a public or business DNS lookup. No business IP, URL, topic/group or input asset is referenced. The container's `127.0.0.11` is its Docker embedded resolver, not the host loopback.

Two nonce-bound handshakes keep the original script paused before probes and after probes so the controller can inspect a live namespace. The fixture acknowledgements are scoped to this run's output directory and have the configured container uid/gid and mode 0600 **before** atomic publication. This adds no broker protocol or general engine permission.

The adapter first installs its existing bridge and namespace policies before Java starts. At the first fixture checkpoint, the test inserts only two additional DNS DROP rules into that exact container's existing dedicated namespace chain, with per-run comments. It matches conntrack's original destination `127.0.0.11:53` because Docker may translate DNS port 53 before filter OUTPUT. The controller pins the namespace by verified container identity, checks both rules with `iptables -C`, and records rule text plus counter snapshots before and after. The rules only tighten the fixture namespace and disappear with it. No host global default, flush, prune or unrelated chain is changed.

The counter parser accepts only `tcp`/`6` and `udp`/`17` for the corresponding exact rule; numeric protocol output is expected with some Ubuntu iptables builds. DROP, the unique owned comment, and numeric packet/byte columns remain mandatory. Raw rule/counter text is attached to the caller's evidence before parsing, so a format failure persists both in `network-observation.json` and `network-acceptance.json`.

Success requires original native SUCCEEDED, exact original TextFileOutput JSON matching the four observed results, both listener counts for their complete lifetime, and both dedicated DNS DROP counter increases. The report records container ID, sourceHash, nonce, output SHA and adapter cleanup of the exact owned container/network/chain. Logs, plans, observation and acceptance JSON remain in the synthetic operation; the protected adapter journal remains in `state_root`.

Failure stops only a container whose protected identity and nonce match this attempt. It preserves the exact journal/resources as RECOVERY_REQUIRED for root inspection, closes listeners, prints `verified=false` and returns nonzero. It does not silently retry or clean resources with an unknown identity.

On failure, stdout/stderr evidence files remain open during the nonce-bound stop and bounded event-reader drain. They close only afterward under the event writer's lock. If the pipe cannot drain, the report explicitly records `eventReaderDrained=false`; late writes are disabled before closing, avoiding a secondary closed-file error that hides the original failure.

```sh
python3 data-governance/kettle-worker/linux/fixtures/test_network_smoke.py
node --check data-governance/kettle-worker/linux/fixtures/network-smoke.js
```

The separate CSV smoke now puts its source in `input/smoke.csv` and refers to `${INPUT_DIR}/smoke.csv`. It must be paired with the worker's new INPUT_DIR snapshot contract; `${WORK_DIR}` remains output only. The RowGenerator network fixture has no input asset and does not depend on INPUT_DIR.
