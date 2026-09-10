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

Each operation starts a fresh Java process under macOS Seatbelt (`sandbox-exec`): network access is denied by default and subprocess forks are always denied, including accesses from JNI. File contents are readable only from that operation's private directory, the original read-only jars/classes, the JDK and necessary system paths; writes are limited to that operation's directory. Other operations' XML and output are excluded. Filesystem metadata lookup and root-directory enumeration are allowed because the JVM loader requires them; this does not grant access to other file contents. `HOME`, Kettle home, JNDI root and temporary paths point to the operation directory. The Kettle hostname is fixed to avoid local-host DNS initialization.

There is no Java SecurityManager. A focused Java probe independently verifies that an adjacent private file cannot be read or written and a localhost socket is rejected with `Operation not permitted`. A missing OS sandbox fails closed. This implementation does not silently run unsandboxed on Linux; a container worker remains a separate deployment requirement.

The private bearer broker owns definition storage, while the untrusted original engine sees only one immutable operation snapshot. Original XML and uploaded files cannot grant additional filesystem or network permissions. Loading a network plugin is **not** evidence that its network operation is accepted. The broker operator may explicitly register endpoints at startup with repeatable `--allow-endpoint 127.0.0.1:15432`, or `--network-policy /private/policy.json`. The policy must be owned by the broker UID, mode 0600, not a symlink, and contain `{"endpoints":[{"host":"127.0.0.1","port":15432}]}`. Optional `expiresAt` is checked at startup. Policy endpoints are frozen for that broker process; neither XML nor per-run request JSON can add endpoints. Validation and capability discovery remain offline even when execution endpoints are registered.

macOS endpoint grants use `(remote tcp4 "localhost:<port>")` with `-Djava.net.preferIPv4Stack=true`. An independent live probe proved that a newly registered `127.0.0.1` listener could be reached while `127.0.0.2` and `::1` at the same port remained denied. Non-loopback host grants fail closed because this Seatbelt filter does not accept arbitrary IPv4 literals; Linux/production egress needs its own implementation. At most 32 exact TCP endpoints are accepted. Health returns `runtimePolicy:{mode,endpoints,validationNetwork,ftpFixturePolicy}` so the gateway can distinguish unconfigured connectivity from native plugin failure. `--ftp-test-policy` additionally supports a short-lived synthetic FTP fixture policy `{purpose:"synthetic-local-ftp",expiresAt,ports:[...]}`; it applies only to Job execution and must enumerate the fixture control/passive ports. Dedicated Kafka preview groups and offset rules remain required before any Kafka preview connection is allowed.

## HTTP contract

Requests and responses use JSON. Error responses are `{ "error": "..." }`, with HTTP 400 for malformed requests, 401 for missing/incorrect authentication, 404 for missing resources and 500 for unexpected worker failures. XML is limited to 2 MiB, DTD/entities are rejected, and transformation steps require unique names and existing hop endpoints.

| Method and path | Request | Response |
| --- | --- | --- |
| `GET /health` | — | `{status:"UP",engine:"original-kettle",sandbox:"macos-seatbelt",protocolVersion:1}` |
| `GET /capabilities` | — | `{type:"capabilities",engine,steps:[...],jobs:[...],networkPolicy:"deny-all",filesystemPolicy:"private-operation-directory",seq,time}` |
| `POST /transformations/validate` | `{xml}` | `{type:"validation",valid:true,name,nodes:[...],seq,time}`; failed native parsing returns `{valid:false,state,errors:[...]}` |
| `PUT /transformations/{id}` | `{xml}` | `{id,sha256,validation:{...}}`; invalid XML is not saved |
| `POST /jobs/validate` | `{xml,inputFiles?}` | Original Job validation, with `kind:"job"`, native entries and hops |
| `PUT /jobs/{id}` | `{xml,inputFiles?}` | `{id,sha256,validation}`; atomically saves the Job with attached child KTR files |
| `POST /runs` | `{transformationId|jobId,runId?,mode:"run"|"preview",previewStep?,rowLimit?,inputFiles?}` | A flat run snapshot `{id,state,mode,fingerprint,createdAt,nodes,eventCount,files,...}` |
| `GET /runs/{id}` | — | Run snapshot |
| `GET /runs/{id}/events?after=0` | — | `{events:[...],nextCursor,state}`; at most 1,000 events per page |
| `GET /runs/{id}/files/{fileName}` | — | Full binary bytes, with `X-Kettle-Partial` indicating incomplete/failed output |
| `POST /runs/{id}/stop` | `{}` | Run snapshot after requesting native `stopAll()` |

IDs accept 1–80 ASCII letters, digits, underscores and hyphens. A supplied `runId` is idempotent for identical operation/XML/preview/sample/input-file content, including after restart when a completed durable record exists. Reuse with different or uncertain content is rejected. Existing operation and record directories are atomically reserved and never overwritten. The broker captures the definition XML at launch; later definition edits do not change the running transform.

`inputFiles` is `[{name:"输入数据.csv",content:"name\nalice\n"}]` or `[{name:"输入数据.csv",contentBase64:"..."}]`. Exactly one content representation is required. Safe UTF-8 basenames, including Chinese and ordinary spaces, are accepted up to 240 UTF-8 bytes; empty/whitespace-only names, `.`, `..`, slashes, backslashes and control characters are rejected. Filenames that collide after NFC normalization and case folding are also rejected to prevent macOS overwrites. At most 20 files of 8 MiB each and 16 MiB total decoded bytes are accepted; the complete JSON request limit is 32 MiB. Files are copied into this run's private output directory before the engine starts. KTR fields refer to `${WORK_DIR}/输入数据.csv`; output fields can use `${WORK_DIR}/输出结果`. RYNEW should obtain these files through its upload UI. The worker does not require users to paste JSON record samples. Binary file support preserves complete bytes; this does not certify an Excel plugin. Encrypted persistent definitions and owner permissions belong to the gateway integration.

Jobs require `NativeJobExecutor` from the separate native Job integration. Their XML is stored as `transformation.kjb` in the operation directory. Child transformations must be attached before validation and referenced as `${WORK_DIR}/child.ktr`; external references are rejected by the native Job loader boundary. Exactly one of `jobId` and `transformationId` is required. Job mode is `run`; preview its child transformation independently. Job run attachments default to the saved immutable attachment set and may be explicitly replaced in the launch request. The launch fingerprint includes the actual attachments used.

File downloads require the same bearer authorization and are confined to a single run's output directory. The broker opens the directory and basename with `O_NOFOLLOW` and rejects symlinks, directories and traversal. Inventory includes `{name,bytes,sha256,role:"input"|"output",partial}`. Files produced by failed, stopped, timed-out or still-running operations remain explicitly partial; they are not successful delivery artifacts. Hashes and downloads during an active write are provisional.

`rowLimit` is **only the per-node/per-direction event sample limit during a normal run**, default 20 and allowed 1–200. The engine continues processing the complete dataset. Preview requires `previewStep`; the worker retains the target and its original ancestors, removes downstream/unrelated nodes, and stops the original transform once the target produces enough rows. Preview still executes those retained original steps. It is not a guarantee of side-effect-free Kafka/database behavior. The offline sandbox remains in force. A terminal output node can write inside the private run directory.

The broker caps concurrent original processes at four. `--timeout` specifies an operation watchdog (1–3,600 seconds, default 120). Stop requests first invoke the original engine, then terminate an unresponsive owned process after five seconds. Timeout and forced termination are explicit states/events. No existing local service is controlled.

## Native metadata and events

A capability has `{id,aliases?,name,category,className,classSource?,loadable,loadError?,defaultXml?}`. `id` is the primary plugin ID; `aliases` preserves original alias IDs. `defaultXml` comes from the original metadata object's `setDefault()/getXML()`. Categories and labels may retain original i18n keys for the frontend's Chinese catalog. `loadable` means metadata/default construction succeeded in this runtime; it does not certify execution, plugin equivalence, connectivity or business readiness. With the original database and password-encoder registries initialized, actual archive discovery produced 189 descriptors with 187 metadata/defaults successfully loaded. The two MongoDB plugins report missing-dependency construction failures. The UI must preserve those distinctions.

The `jobs` array is also generated from original descriptors and original JobEntry constructors/getXML: 67 core entries are loaded, including original `SPECIAL`, `TRANS` and `FTP_PUT` with defaults and class provenance. `executionSupported` is true for those three accepted entry types; the other discovered entries remain outside this executor's current boundary. Extra plugins in the archive's separate plugin directories are not silently equated with this core runtime catalog.

Validation nodes contain `{name,pluginId,className,classSource,inputFields:[{name,type,length,precision,origin}],fields:[{name,type,length,precision,origin}],inputFieldError?,fieldError?}`. Input fields come directly from original `TransMeta.getPrevStepFields(step)` and output fields from `getStepFields(step)`; disconnected nodes are not guessed to be upstream. Errors remain attached to the affected node. Even field discovery is sandboxed because some plugins may try to query their source. Import/validation never grants network permissions. Runtime row `fieldsMeta` includes the same length, precision and origin metadata.

Original ERROR, MINIMAL and BASIC logs are observed with transformation verbosity BASIC. The worker preserves the native WriteToLog node's configured log level instead of rewriting it. Events are filtered to the original transformation/metadata log channels and their descendants, include native `node`/`channel`/`level`, and are bounded to 500 messages of 8,192 characters. Credential values found in submitted XML are redacted. DETAILED, DEBUG and ROWLEVEL are not enabled by default. `logTruncated` and `logEventCount` report the observation bound without limiting engine rows. The original `log_level_basic` WriteToLog configuration was verified to appear in real events with its marker and redacted credential text; ERROR events remain visible.

Every event includes `seq` (monotonic within a run), `type`, and `time` (Unix milliseconds):

```json
{"seq":4,"type":"row","time":0,"node":"native-script","copy":0,"direction":"written","rowNumber":1,"fields":{"name":"alice","greeting":"ALICE!"},"fieldsMeta":[{"name":"name","type":"String"},{"name":"greeting","type":"String"}]}
```

Row directions are `read`, `written`, and `error`; values are normalized with the original Kettle value metadata and bounded to 4,096 characters, with null retained. These events are bounded observation samples. Live and final `metrics.nodes` use native counters `{node,copy,status,read,written,input,output,rejected,errors}`. The listener is attached after `prepareExecution` and before `startThreads`, so fast input cannot finish before observation begins.

State events report `PREPARING`, `RUNNING` and `STOPPING`. Terminal states are `SUCCEEDED`, `FAILED`, `STOPPED`, `PREVIEW_COMPLETE` and `TIMED_OUT`. Native `Trans.getErrors()` takes precedence over `Result.getResult()`; the latter is retained as diagnostic `resultBoolean` and never used alone to declare success. Preview truncation is explicitly reported. JVM failures without an engine terminal event produce a broker terminal event. Run snapshots include final native counters, exit code, creation/finish Unix seconds, event count and private output inventory.

## Verified scope and remaining work

Sixteen focused acceptance tests passed on 2026-09-10, including the separately compiled native Job executor. They cover original CSV file input → original modified-JavaScript step → original text-file output with exact Chinese/ASCII file readback; per-node rows/fields/counters; actual upstream metadata excluding disconnected nodes; preview with no downstream file; stopping an active original Delay step; original script failure with nonzero errors; 350-row execution despite two-row event sampling; immutable/idempotent launch; XML guards; OS file/network denial without SecurityManager; trusted policy permissions and host restrictions; safe UTF-8 basenames and macOS filename collision refusal; automatic Kafka preview overrides without changing saved/original XML; native BASIC logging and redaction; native Job validation/execution through the broker; and real HTTP bearer rejection, browser Origin rejection, Chinese binary upload, output byte/hash download and typed events. Four additional real broker restart tests cover durable recovery.

The archive's `KafkaConsumer`, `KafkaProducer`, `JsonInput`, `DBLookup`, `FilterRows`, `CsvInput`, `ScriptValueMod` and `TextFileOutput` metadata are verified to originate from the original custom jar. The separate Job proof covers original `SPECIAL → TRANS → FTP_PUT`, exact 98-byte FTP readback and failure/stop success-hop gating. These are not full original-business equivalence or production deployment acceptance. Legacy Kafka fixture validation, persisted gateway ownership/versioning and browser end-to-end acceptance remain separate integration work.

## PostgreSQL driver and original metadata boundary

The original PostgreSQL 9.3 JDBC driver reaches the isolated PostgreSQL 17 fixture but cannot authenticate SCRAM (`authentication type 10`). No HBA, role password or existing service was changed. An explicit runtime-only driver extension uses the project's existing PostgreSQL JDBC 42.7.7 jar, validates its local Maven `.jar.sha1`, records its SHA-256/source/priority, and places it before the original JDBC jar. It does not contain or replace any Kettle classes:

```sh
python3 tools/data-governance/kettle_worker.py install-postgres-jdbc \
  --runtime /absolute/private/kettle-worker-runtime \
  --jdbc-jar /absolute/local/maven/postgresql-42.7.7.jar
```

The tested driver SHA-256 is `157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460`. Installing a driver is an explicit preparation step and requires restarting the owned broker to load the updated manifest. Fresh `prepare` reconstructs original archive precedence, so reapply any approved JDBC extension before serving.

`test_kettle_postgres.py` reads credentials only from `KETTLE_POSTGRES_CREDENTIALS`, never prints them, and permits only the isolated `127.0.0.1:15432` endpoint. It proves original `TableInput → ScriptValueMod → SelectValues → TextFileOutput` against a two-row read-only VALUES query. Optional `KETTLE_POSTGRES_LOOKUP_PROOF=1` creates a unique `kettle_v2_<nonce>` schema/table using original ExecSQL, executes original DBLookup, verifies exact output and drops only that owned schema in `finally`.

The live proof also exposed an original TextFileOutput limitation: PostgreSQL unbounded TEXT metadata can carry length 2,147,483,647, causing `convertStringToBinaryString` to allocate beyond the JVM limit. Setting output widths to 128 avoids that allocation but the original writer pads those fields. The verified original-tool configuration is SelectValues metadata conversion to String with length `-1`, which normalizes the JDBC width and yields the expected unpadded bytes. No replacement writer or modified original jar is used.

## Durable execution and broker restart

Before `Popen`, the broker durably writes an immutable `run-records/<runId>/intent.json` containing the fingerprint and a unique launch nonce. That record directory is outside the original JVM's allowed filesystem roots. Canonical events and terminal metadata are also written there, with atomic JSON replacement, fsync and a completed-event SHA-256. The operation-directory `events.ndjson` remains a compatibility copy, not the authoritative record for new runs. The sandbox additionally prohibits hard-link creation.

The broker exposes a terminal success/failure only after the owned process exits and terminal metadata is durably finalized. A nested `job-transformation` completion is not a parent Job completion. On restart, finalized records are restored read-only: events, counters and artifacts remain available, and an identical `runId` submission returns that existing result without starting a process or modifying the record.

An unfinished, inconsistent, corrupt or reserved-but-unsubmitted record becomes `RECOVERY_REQUIRED`. Its last observed counters and partial files remain inspectable. It is never automatically resumed, replayed, overwritten or treated as a success. A request reusing that ID is rejected even when the submitted fingerprint matches. Existing legacy operation directories can be recovered read-only from a valid native terminal event; their original fingerprint is unknown, so re-submission of a legacy ID is rejected. Legacy/incomplete records with no verified control nonce cannot be stopped automatically.

Control does not signal saved PIDs. A live supervisor uses the original `Popen` stdin pipe, which stays tied to its own child through `sandbox-exec → JVM` exec. It also writes a nonce-bound `control.stop` file to the owned operation directory. The JVM control bridge accepts only its launch nonce, supports a stop arriving before engine initialization, and delegates STOP to the original Trans/Job controller. A recovered supervisor can request cooperative stop through that file, but keeps `RECOVERY_REQUIRED` because prior external effects remain unknown. An unresponsive owned JVM can be instructed to halt itself through the same pipe/file; no reconstructed PID is killed. Broker stdin EOF requests native stop, limiting orphan execution without pretending that missing completion evidence exists.

`test_kettle_worker_recovery.py` starts only new private random-port brokers. Real restart tests prove completed event/file equality and idempotency, crash-before-completion refusal to replay, immediate post-spawn stop across exec transition, and refusal to execute or signal a corrupt reserved identity. Root broker port 19162 is not controlled by this test.

## Original Kafka commit semantics and preview boundary

The isolated Kafka 3.9.2/ZooKeeper fixture proved actual original `KafkaConsumer → ScriptValueMod → KafkaProducer` execution for eight messages in batches of five and three. The original producer converts String values to `byte[]`; its configured serializer must therefore be the original `kafka.serializer.DefaultEncoder`, not StringEncoder.

The original distribution does **not** provide an end-to-end transaction. With `auto.commit.enable=true`, the source ZooKeeper offset advanced to five even when the downstream producer failed. With auto-commit disabled, a normal successful transformation still commits through the original custom `Trans$3.stepFinished`; an observed producer-error run did not commit. These are measured original behaviors, not a promise of exactly-once delivery, rollback or atomic source/output acknowledgement.

That same original completion callback also invokes `consumer.commitOffsets()` during preview, even when auto-commit is false. The worker therefore enforces a separate preview boundary automatically. After retaining the target's real upstream graph and before `prepareExecution`, every KafkaConsumer in that execution copy receives a dedicated `kettle-v2-<32 lowercase hex UUID>-preview` group derived from the immutable run ID and step name, plus `auto.commit.enable=false`. The saved definition and the operation's original `transformation.ktr` remain byte-for-byte unchanged; users do not need to edit their business group to preview. A second guard validates the effective metadata before any threads start, so unsafe configuration cannot bypass the execution boundary.

The original submitted XML and native-serialized effective preview graph have `originalXmlSha` and `effectiveXmlSha`, recorded by `execution-snapshot` and persisted into the run snapshot. `transformation.effective.ktr` is stored separately inside the private operation directory. Each `preview-override` event names the node, `originalGroup`, `effectiveGroup`, `originalAutoCommit` and `effectiveAutoCommit`, plus the two hashes. Normal run mode applies no Kafka overrides, copies original XML unchanged, and reports equal original/effective hashes. These hashes describe the submitted and effective configurations; they do not claim engine side effects can be rolled back.

After prepare and before threads start, the worker replaces only the data object's `ConsumerConnector` reference with an interface proxy. All commitOffsets overloads are blocked and emit `preview-offset-commit-blocked`; every other operation delegates to the original connector. Original jars and operators are unchanged.

The live fixture verified both preview and preview-stop against a saved definition using an existing normal-run group and auto-commit=true. The worker automatically derived different effective groups for the two runs, explicit commit-blocked events occurred, ZooKeeper and broker offsets for those actual effective groups remained absent, group owners were released, the original group's offset remained eight, target-topic rows remained eight even after stop, and stop completed without forced termination. Saved definition and original execution XML bytes remained unchanged. This boundary does not rewrite normal-run semantics or claim an original Kafka 0.8 distributed transaction.
