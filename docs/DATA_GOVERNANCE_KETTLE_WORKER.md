# Original Kettle worker protocol v1

The worker executes the actual Kettle `Trans` and original step implementations from the externally supplied `kettle6.1.3.0.zip`. It does not translate steps into NiFi, replace operators with local implementations, or execute the original business attachments during acceptance. The custom `kettle-6.1.0.7.36.jar` is deliberately first in the classpath. Jar bytes and their archive entries are recorded with SHA-256 in the private runtime manifest.

## Start and acceptance

Run from a verified data-governance worktree. Generated classes, jars, XML snapshots, events and output stay outside Git.

```sh
python3 tools/data-governance/kettle_worker.py prepare \
  --archive /Volumes/KINGSTON/kettle6.1.3.0.zip \
  --runtime /absolute/private/kettle-worker-runtime
python3 tools/data-governance/kettle_worker.py serve \
  --runtime /absolute/private/kettle-worker-runtime --port 19162 --timeout 120
KETTLE_WORKER_RUNTIME=/absolute/private/kettle-worker-runtime \
  python3 data-governance/kettle-worker/tests/test_kettle_worker.py
```

Java 17 is used, with its default macOS location in the preparation script. `prepare()` also accepts an explicit Java home when called as a Python function. The runtime must be prepared for the same filesystem location from which it is served. Do not distribute the archive or its jars through source commits.

The broker binds only `127.0.0.1:19162`. Every request, including health, requires `Authorization: Bearer <token>`. The token is generated in `<runtime>/.broker-token` with mode 0600 and must be read privately by the authenticated RYNEW backend proxy. It must never be sent to browser JavaScript, logged or committed. Browser Origin headers are rejected and no CORS permission is returned.

## OS isolation

Each operation starts a fresh Java process under macOS Seatbelt (`sandbox-exec`): all network access and subprocess forks are denied, including accesses from JNI. File contents are readable only from that operation's private directory, the original read-only jars/classes, the JDK and necessary system paths; writes are limited to that operation's directory. Other operations' XML and output are excluded. Filesystem metadata lookup and root-directory enumeration are allowed because the JVM loader requires them; this does not grant access to other file contents. `HOME`, Kettle home, JNDI root and temporary paths point to the operation directory. The Kettle hostname is fixed to avoid local-host DNS initialization.

There is no Java SecurityManager. A focused Java probe independently verifies that an adjacent private file cannot be read or written and a localhost socket is rejected with `Operation not permitted`. A missing OS sandbox fails closed. This implementation does not silently run unsandboxed on Linux; a container worker remains a separate deployment requirement.

The private bearer broker owns definition storage, while the untrusted original engine sees only one immutable operation snapshot. Original XML cannot grant additional filesystem or network permissions. This offline policy intentionally blocks database, Kafka, FTP and other network connections. Loading such a plugin is **not** evidence that its network operation is accepted. Production connection grants and dedicated preview consumer groups require a separately reviewed execution policy.

## HTTP contract

Requests and responses use JSON. Error responses are `{ "error": "..." }`, with HTTP 400 for malformed requests, 401 for missing/incorrect authentication, 404 for missing resources and 500 for unexpected worker failures. XML is limited to 2 MiB, DTD/entities are rejected, and transformation steps require unique names and existing hop endpoints.

| Method and path | Request | Response |
| --- | --- | --- |
| `GET /health` | — | `{status:"UP",engine:"original-kettle",sandbox:"macos-seatbelt",protocolVersion:1}` |
| `GET /capabilities` | — | `{type:"capabilities",engine,steps:[...],networkPolicy:"deny-all",filesystemPolicy:"private-operation-directory",seq,time}` |
| `POST /transformations/validate` | `{xml}` | `{type:"validation",valid:true,name,nodes:[...],seq,time}`; failed native parsing returns `{valid:false,state,errors:[...]}` |
| `PUT /transformations/{id}` | `{xml}` | `{id,sha256,validation:{...}}`; invalid XML is not saved |
| `POST /runs` | `{transformationId,runId?,mode:"run"|"preview",previewStep?,rowLimit?,inputFiles?}` | A flat run snapshot `{id,state,mode,fingerprint,createdAt,nodes,eventCount,files,...}` |
| `GET /runs/{id}` | — | Run snapshot |
| `GET /runs/{id}/events?after=0` | — | `{events:[...],nextCursor,state}`; at most 1,000 events per page |
| `GET /runs/{id}/files/{fileName}` | — | Full binary bytes, with `X-Kettle-Partial` indicating incomplete/failed output |
| `POST /runs/{id}/stop` | `{}` | Run snapshot after requesting native `stopAll()` |

IDs accept 1–80 ASCII letters, digits, underscores and hyphens. A supplied `runId` is idempotent for identical operation/XML/preview/sample/input-file content within the broker lifetime. Reuse with different content is rejected. Existing operation directories are never overwritten, including after a broker restart. The broker captures the definition XML at launch; later definition edits do not change the running transform.

`inputFiles` is `[{name:"input.csv",content:"name\nalice\n"}]` or `[{name:"input.csv",contentBase64:"..."}]`. Exactly one content representation is required. Names are flat, safe filenames, with at most 20 files of 2 MiB each and 16 MiB total decoded bytes. Files are copied into this run's private output directory before the engine starts. KTR fields refer to `${WORK_DIR}/input.csv`; output fields can use `${WORK_DIR}/result`. RYNEW should obtain these files through its upload UI. The worker does not require users to paste JSON record samples. Binary file support preserves complete bytes; this does not certify an Excel plugin. Retained-run recovery and encrypted persistent definitions belong to the gateway integration.

File downloads require the same bearer authorization and are confined to a single run's output directory. The broker opens the directory and basename with `O_NOFOLLOW` and rejects symlinks, directories and traversal. Inventory includes `{name,bytes,sha256,role:"input"|"output",partial}`. Files produced by failed, stopped, timed-out or still-running operations remain explicitly partial; they are not successful delivery artifacts. Hashes and downloads during an active write are provisional.

`rowLimit` is **only the per-node/per-direction event sample limit during a normal run**, default 20 and allowed 1–200. The engine continues processing the complete dataset. Preview requires `previewStep`; the worker retains the target and its original ancestors, removes downstream/unrelated nodes, and stops the original transform once the target produces enough rows. Preview still executes those retained original steps. It is not a guarantee of side-effect-free Kafka/database behavior. The offline sandbox remains in force. A terminal output node can write inside the private run directory.

The broker caps concurrent original processes at four. `--timeout` specifies an operation watchdog (1–3,600 seconds, default 120). Stop requests first invoke the original engine, then terminate an unresponsive owned process after five seconds. Timeout and forced termination are explicit states/events. No existing local service is controlled.

## Native metadata and events

A capability has `{id,aliases?,name,category,className,classSource?,loadable,loadError?,defaultXml?}`. `id` is the primary plugin ID; `aliases` preserves original alias IDs. `defaultXml` comes from the original metadata object's `setDefault()/getXML()`. Categories and labels may retain original i18n keys for the frontend's Chinese catalog. `loadable` means metadata/default construction succeeded in this runtime; it does not certify execution, plugin equivalence, connectivity or business readiness. The initial actual archive discovery produced 189 descriptors, with 173 metadata/defaults successfully loaded and 16 explicit failures. The UI must preserve those distinctions.

Validation nodes contain `{name,pluginId,className,classSource,fields:[{name,type}],fieldError?}`. Field discovery uses the original `TransMeta.getStepFields`; errors remain attached to the affected node. Even field discovery is sandboxed because some plugins may try to query their source. Import/validation never grants network permissions.

Every event includes `seq` (monotonic within a run), `type`, and `time` (Unix milliseconds):

```json
{"seq":4,"type":"row","time":0,"node":"native-script","copy":0,"direction":"written","rowNumber":1,"fields":{"name":"alice","greeting":"ALICE!"},"fieldsMeta":[{"name":"name","type":"String"},{"name":"greeting","type":"String"}]}
```

Row directions are `read`, `written`, and `error`; values are normalized with the original Kettle value metadata and bounded to 4,096 characters, with null retained. These events are bounded observation samples. Live and final `metrics.nodes` use native counters `{node,copy,status,read,written,input,output,rejected,errors}`. The listener is attached after `prepareExecution` and before `startThreads`, so fast input cannot finish before observation begins.

State events report `PREPARING`, `RUNNING` and `STOPPING`. Terminal states are `SUCCEEDED`, `FAILED`, `STOPPED`, `PREVIEW_COMPLETE` and `TIMED_OUT`. Native `Trans.getErrors()` takes precedence over `Result.getResult()`; the latter is retained as diagnostic `resultBoolean` and never used alone to declare success. Preview truncation is explicitly reported. JVM failures without an engine terminal event produce a broker terminal event. Run snapshots include final native counters, exit code, creation/finish Unix seconds, event count and private output inventory.

## Verified scope and remaining work

Ten synthetic acceptance tests passed on 2026-09-10. They cover original CSV file input → original modified-JavaScript step → original text-file output with exact Chinese/ASCII file readback; per-node rows/fields/counters; preview with no downstream file; stopping an active original Delay step; original script failure with nonzero errors; 350-row execution despite two-row event sampling; immutable/idempotent launch; XML guards; OS file/network denial without SecurityManager; and real HTTP bearer rejection, browser Origin rejection, binary upload, output byte/hash download and typed events.

The archive's `KafkaConsumer`, `KafkaProducer`, `JsonInput`, `DBLookup`, `FilterRows`, `CsvInput`, `ScriptValueMod` and `TextFileOutput` metadata are verified to originate from the original custom jar. This is not full original-business equivalence or production deployment acceptance. Original KJB `SPECIAL → TRANS → FTP_PUT`, target-specific network policy, persisted gateway ownership/versioning, and browser end-to-end acceptance are separate required integration work. The Java entrypoint reserves operation `job` for `NativeJobExecutor.run(Path)` to keep native Job integration independent.
