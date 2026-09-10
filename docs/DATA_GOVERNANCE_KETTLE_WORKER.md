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

There is no Java SecurityManager. A focused Java probe independently verifies that an adjacent private file cannot be read or written and a localhost socket is rejected with `Operation not permitted`. A missing OS sandbox fails closed. Linux now dispatches through the separately reviewed Docker adapter only when a trusted `--linux-config` is supplied; it never falls back to Seatbelt or unisolated Java.

The private bearer broker owns definition storage, while the untrusted original engine sees only one immutable operation snapshot. Original XML and uploaded files cannot grant additional filesystem or network permissions. Loading a network plugin is **not** evidence that its network operation is accepted. The broker operator may explicitly register endpoints at startup with repeatable `--allow-endpoint 127.0.0.1:15432`, or `--network-policy /private/policy.json`. The policy must be owned by the broker UID, mode 0600, not a symlink, and contain `{"endpoints":[{"host":"127.0.0.1","port":15432}]}`. Optional `expiresAt` is checked at startup. Policy endpoints are frozen for that broker process; neither XML nor per-run request JSON can add endpoints. Saving a transformation uses the original XML loader without field queries and remains offline. Capability discovery also remains offline. Explicit transformation field discovery uses only the broker’s registered endpoints; it cannot add addresses from XML or request JSON.

macOS endpoint grants use `(remote tcp4 "localhost:<port>")` with `-Djava.net.preferIPv4Stack=true`. An independent live probe proved that a newly registered `127.0.0.1` listener could be reached while `127.0.0.2` and `::1` at the same port remained denied. Non-loopback host grants fail closed because this Seatbelt filter does not accept arbitrary IPv4 literals; Linux/production egress needs its own implementation. At most 32 exact TCP endpoints are accepted. Health returns `runtimePolicy:{mode,endpoints,validationNetwork,ftpFixturePolicy}` so the gateway can distinguish unconfigured connectivity from native plugin failure. `--ftp-test-policy` additionally supports a short-lived synthetic FTP fixture policy `{purpose:"synthetic-local-ftp",expiresAt,ports:[...]}`; it applies only to Job execution and must enumerate the fixture control/passive ports. Dedicated Kafka preview groups and offset rules remain required before any Kafka preview connection is allowed.

## HTTP contract

Requests and responses use JSON. Error responses are `{ "error": "..." }`, with HTTP 400 for malformed requests, 401 for missing/incorrect authentication, 404 for missing resources and 500 for unexpected worker failures. XML is limited to 2 MiB, DTD/entities are rejected, and transformation steps require unique names and existing hop endpoints.

| Method and path | Request | Response |
| --- | --- | --- |
| `GET /health` | — | `{status:"UP",engine:"original-kettle",sandbox:"macos-seatbelt",protocolVersion:1}` |
| `GET /capabilities` | — | `{type:"capabilities",engine,steps:[...],jobs:[...],networkPolicy:"deny-all",filesystemPolicy:"private-operation-directory",seq,time}` |
| `POST /transformations/validate` | `{xml}` | `{type:"validation",valid,metadataLoaded:true,validationScope:"metadata-only",fieldsRequested:true,fieldsResolved,name,nodes:[...],fieldDiagnostics:[...],seq,time}`; failed native parsing returns `{valid:false,state,errors:[...]}` |
| `PUT /transformations/{id}` | `{xml}` | `{id,sha256,validation:{validationScope:"xml-load",metadataLoaded:true,fieldsRequested:false,...}}`; original XML is loaded offline without field queries; invalid XML is not saved |
| `POST /jobs/validate` | `{xml,inputFiles?}` | Original Job validation, with `kind:"job"`, native entries and hops |
| `PUT /jobs/{id}` | `{xml,inputFiles?}` | `{id,sha256,validation}`; atomically saves the Job with attached child KTR files |
| `POST /runs` | `{transformationId|jobId,runId?,mode:"run"|"preview",previewStep?,rowLimit?,inputFiles?}` | A flat run snapshot `{id,state,mode,fingerprint,createdAt,nodes,eventCount,files,...}` |
| `GET /runs/{id}` | — | Run snapshot |
| `GET /runs/{id}/events?after=0` | — | `{events:[...],nextCursor,state}`; at most 1,000 events per page |
| `GET /runs/{id}/files/{fileName}` | — | Full binary bytes, with `X-Kettle-Partial` indicating incomplete/failed output |
| `POST /runs/{id}/stop` | `{}` | Run snapshot after requesting native `stopAll()` |

IDs accept 1–80 ASCII letters, digits, underscores and hyphens. A supplied `runId` is idempotent for identical operation/XML/preview/sample/input-file content, including after restart when a completed durable record exists. Reuse with different or uncertain content is rejected. Existing operation and record directories are atomically reserved and never overwritten. The broker captures the definition XML at launch; later definition edits do not change the running transform.

`inputFiles` is `[{name:"输入数据.csv",content:"name\nalice\n"}]` or `[{name:"输入数据.csv",contentBase64:"..."}]`. Exactly one content representation is required. Safe UTF-8 basenames, including Chinese and ordinary spaces, are accepted up to 240 UTF-8 bytes; empty/whitespace-only names, `.`, `..`, slashes, backslashes and control characters are rejected. Filenames that collide after NFC normalization and case folding are also rejected to prevent macOS overwrites. At most 20 files of 8 MiB each and 16 MiB total decoded bytes are accepted; the complete JSON request limit is 32 MiB. Files are copied **only** into this run's private `input/` directory before the engine starts. KTR read fields refer to `${INPUT_DIR}/输入数据.csv`; output fields use `${WORK_DIR}/输出结果`, where WORK_DIR remains `output/`. There are no output-directory aliases or copies of input assets. RYNEW should obtain these files through its upload UI. The worker does not require users to paste JSON record samples. Binary file support preserves complete bytes; this does not certify an Excel plugin. Encrypted persistent definitions and owner permissions belong to the gateway integration.

Jobs require `NativeJobExecutor` from the separate native Job integration. Their XML is stored as `transformation.kjb` in the operation directory. Child transformations must be attached before validation and referenced as `${INPUT_DIR}/child.ktr`; external references are rejected by the native Job loader boundary. Exactly one of `jobId` and `transformationId` is required. Job mode is `run`; preview its child transformation independently. Job run attachments default to the saved immutable attachment set and may be explicitly replaced in the launch request. The launch fingerprint includes the actual attachments used. Both Job metadata and Job runtime must expose the controlled INPUT_DIR variable before resolving or delegating the child transformation.

File downloads require the same bearer authorization and are confined to a single run's output directory. The broker opens the directory and basename with `O_NOFOLLOW` and rejects symlinks, directories and traversal. Inventory includes `{name,bytes,sha256,role:"input"|"output",partial}`. Files produced by failed, stopped, timed-out or still-running operations remain explicitly partial; they are not successful delivery artifacts. Hashes and downloads during an active write are provisional.

New intents carry `inputLayout:"separate"` and an independent `inputs:[{name,bytes,sha256}]` manifest. Every file found in new `output/` is an output, including a legitimate output with the same basename as an input. The input directory and uploaded child KTRs are never served by the run-artifact download endpoint. Old read-only records without this layout marker retain the historical `inputNames` role filter, and known legacy input-role files cannot be downloaded as run outputs. Frozen historical input snapshots are not rewritten. Old read attributes using `${WORK_DIR}/input.csv` must be migrated in a new definition version; the worker does not recreate the unsafe output alias.

The native FTP wildcard `.*` proof uses a fresh private loopback fixture and three uploaded assets including a child KTR and synthetic secret marker. Original START→TRANS→FTP_PUT sends only `output/success.txt` (98 bytes), and FTP RETR matches the native file hash. No CSV/KTR input asset appears remotely or in the run-output download endpoint. The fixture is stopped after the test. This proves that uploading the WORK_DIR output tree does not automatically include the input tree; arbitrary user scripts still keep their original semantics within the configured policy.

```sh
KETTLE_WORKER_RUNTIME=/absolute/private/kettle-worker-runtime \
KETTLE_FTP_PYTHON=/absolute/existing/ftp-venv/bin/python \
  python3 data-governance/kettle-worker/tests/test_kettle_output_delivery_isolation.py
```

Default named parameters are handled by the original APIs: the original Trans constructor copies metadata parameters and activates metadata defaults, and original `prepareExecution` activates both Trans and TransMeta. The worker additionally activates metadata defaults before metadata-only field discovery, and rejects the reserved WORK_DIR/INPUT_DIR/JOB_DIR parameter names before constructing Trans, matching the Job boundary. A real `${file_prefix}` default produced `原生默认前缀.csv` with exact contents; unknown parameter values are not rewritten or evaluated by a substitute interpreter.

The root XML attribute `data-rynew-timezone` freezes the execution timezone. Absence means `Asia/Shanghai`; an explicitly empty value is invalid. Accepted values are `UTC` or an exact, existing IANA region name containing `/`; whitespace, abbreviations such as CST/EST, invented GMT offsets and arbitrary JVM arguments are rejected. macOS passes only the validated value through `-Duser.timezone`, and Java independently checks its ZoneId catalog before initializing the original engine. `execution-timezone` events and run snapshots record requested `executionTimeZone` and actual `effectiveTimeZone`.

A Job chooses its JVM timezone once from its own root attribute. Native child TRANS steps run in that same JVM; a child KTR's root timezone attribute does not change the parent Job's timezone. Submitted XML is not rewritten by the worker. Old finalized records are not replayed or reinterpreted merely because the new default is Shanghai.

Three dedicated tests use original ScriptValueMod and Java Date/SimpleDateFormat, proving both critical epochs: `-1 ms` gives UTC `1969-12-31 23:59:59` versus Shanghai `1970-01-01 07:59:59`; `86399000 ms` gives UTC `1970-01-01 23:59:59` versus Shanghai `1970-01-02 07:59:59`. The no-attribute default matches Shanghai, and a Shanghai parent Job with an explicitly UTC child KTR still emits Shanghai dates while both XML files remain byte-for-byte unchanged.

`rowLimit` is **only the per-node/per-direction event sample limit during a normal run**, default 20 and allowed 1–200. The engine continues processing the complete dataset. Preview requires `previewStep`; the worker retains the target and its original ancestors, removes downstream/unrelated nodes, and stops the original transform once the target produces enough rows. Preview still executes those retained original steps. It is not a guarantee of side-effect-free Kafka/database behavior. The configured OS sandbox remains in force. Output execution requires an explicit run.

Preview projection happens before a native `Trans` is constructed. The effective snapshot now includes `previewProjection:{target,retainedOriginalNodes,excludedOriginalNodes,collectors}` with original/effective XML hashes. Output-category steps, known SQL/HTTP-write/process/nested-execution steps require an explicit run; preview rejects those targets or such retained ancestors before step preparation. Ordinary user script semantics remain unchanged within the trusted OS policy.

Unselected branches require more than deleting hops. Original FilterRows true/false references and SwitchCase case/default references are retargeted only in the effective metadata to original `DummyTransMeta` collectors, with explicit generated names and `previewCollector:true`. Original output nodes are absent from the executable graph and their names never appear as executed output metrics. Normal fan-out hops preserve their original order and target-copy count so native copy/distribute behavior does not change which rows reach the selected branch. Information-stream ancestors are retained. The original error target, when applicable, is likewise retargeted to its collector rather than executing an excluded sink.

The original archive's `TransPreviewFactory.generatePreviewTransformation` was inspected directly: it constructs one supplied native metadata step followed by native Dummy and a hop. It is not a whole-graph ancestor extractor. The worker follows that original harmless-collector pattern while preserving the selected graph's actual native routing metadata. SwitchCase already-resolved target objects are updated directly; calling its original `searchInfoAndTargetSteps` a second time can dereference the default stream's null subject and is avoided.

Five additional real-native tests verify linear CSV→Script preview has exactly those two original metrics and no file, full run still writes exact output, FilterRows preview emits only matching rows, SwitchCase branch and router previews preserve case/default routing without output nodes, ordinary fan-out selects the same rows as full native execution, and an output target is rejected before preparation. Collectors are visible as actual Dummy helpers, never represented as a successful external write.

The broker caps concurrent original processes at four. `--timeout` specifies an operation watchdog (1–3,600 seconds, default 120). Stop requests first invoke the original engine, then terminate an unresponsive owned process after five seconds. Timeout and forced termination are explicit states/events. No existing local service is controlled.

## Native metadata and events

A capability has `{id,aliases?,name,category,className,classSource?,loadable,loadError?,defaultXml?}`. `id` is the primary plugin ID; `aliases` preserves original alias IDs. `defaultXml` comes from the original metadata object's `setDefault()/getXML()`. Categories and labels may retain original i18n keys for the frontend's Chinese catalog. `loadable` means metadata/default construction succeeded in this runtime; it does not certify execution, plugin equivalence, connectivity or business readiness. With the original database and password-encoder registries initialized, actual archive discovery produced 189 descriptors with 187 metadata/defaults successfully loaded. The two MongoDB plugins report missing-dependency construction failures. The UI must preserve those distinctions.

The `jobs` array is also generated from original descriptors and original JobEntry constructors/getXML: 67 core entries are loaded, including original `SPECIAL`, `TRANS` and `FTP_PUT` with defaults and class provenance. `executionSupported` is true for those three accepted entry types; the other discovered entries remain outside this executor's current boundary. Extra plugins in the archive's separate plugin directories are not silently equated with this core runtime catalog.

Loaded Step/Job capabilities additionally expose optional `configurationTemplateXml`, `allocationApplied`, `allocationMethod`, `allocationArguments`, `configurationTemplateReadback` and `allocationReason`. `defaultXml` is preserved exactly. The optional template is built on a separate original metadata instance: Step metadata uses `setDefault`; Job metadata uses its constructor defaults and a public no-argument `setDefault` when present. Only a public, non-static, void `allocate` method with one to six primitive `int` parameters is eligible, with every argument set to one. Method selection is deterministic, preferring the largest eligible arity. No arbitrary setter, task execution, `getStep`, `Trans.prepareExecution` or Job execution is invoked.

The resulting XML comes directly from original `getXML()` and must be accepted by original `loadXML()` on another fresh metadata instance before `allocationApplied` and `configurationTemplateReadback` become true. No network grant is provided to capability discovery. Missing methods or native allocation/serialization/readback failures retain `configurationTemplateXml=defaultXml`, set `allocationApplied=false`, and report a reason; they do not change the plugin's independent `loadable` flag.

The actual archive yielded 76 Step templates and 15 Job templates with successful native allocation/readback. Another 29 Step allocation/readback attempts failed explicitly and retained their original defaults. This is an XML editing skeleton, not a complete UI schema: it does not invent required fields, value ranges, enum choices, dynamic dialogs or proof that an incomplete row can execute. The gateway may transmit it as `configurationTemplateXmlBase64` to support generic recursive forms and adding array rows.

Four focused metadata-only tests verify SortRows and TableOutput original empty field arrays remain unchanged while their templates contain one native field row, and SelectValues `allocate(int,int,int)` exposes native select/remove/meta rows. Original boolean encodings are retained: SortRows uses `ascending=N`, TableOutput uses `use_batch=Y`, while SelectValues metadata uses literal `date_format_lenient=false` and `lenient_string_to_number=false`. The form must preserve those native encodings. Tests also verify native reload, fallback equality, argument bounds, Job template readback and absence of any task RUNNING event:

```sh
KETTLE_WORKER_RUNTIME=/absolute/private/kettle-worker-runtime \
  python3 data-governance/kettle-worker/tests/test_kettle_metadata_templates.py
```

Field-discovery nodes contain `{name,pluginId,className,classSource,inputFields:[{name,type,length,precision,origin}],fields:[{name,type,length,precision,origin}],inputFieldError?,inputFieldErrorMessage?,fieldError?,fieldErrorMessage?}`. Input fields come directly from original `TransMeta.getPrevStepFields(step)` and output fields from `getStepFields(step)`; disconnected nodes are not guessed to be upstream. A native field-query exception sets `valid:false` and `fieldsResolved:false` and is included in `fieldDiagnostics:[{node,direction,errorClass,message}]`. The native XML still loaded successfully (`metadataLoaded:true`). The explicit scope is `metadata-only`: no `TransMeta.checkSteps` or transformation execution occurs, and a plugin returning an empty schema does not prove its configuration can run. Saving uses a separate internal `load` operation with `validationScope:"xml-load"`, no field calls and no network. Explicit field discovery may query approved sources through the same exact registered endpoint policy as a run; capability discovery and Job loading remain offline. Runtime row `fieldsMeta` includes the same length, precision and origin metadata.

Rows preserve the original `ValueMetaInterface.getString` formatting, including large integer digits and configured whitespace. If a field cannot be converted (for example a CSV lazy binary field replaced by a JavaScript String while the original metadata still declares lazy storage), that key is omitted from `fields` and `fieldErrors:[{name,type,errorClass,message}]` records the failure. No placeholder or false null is manufactured. Genuine native nulls remain null. Error messages use the credential redactor and are capped at 1,024 characters; the observer does not change the engine result or metadata to make a failing observation appear valid.

Original ERROR, MINIMAL and BASIC logs are observed with transformation verbosity BASIC. The worker preserves the native WriteToLog node's configured log level instead of rewriting it. Events are filtered to the original transformation/metadata log channels and their descendants, include native `node`/`channel`/`level`, and are bounded to 500 messages of 8,192 characters. Credential values found in submitted XML are redacted. DETAILED, DEBUG and ROWLEVEL are not enabled by default. `logTruncated` and `logEventCount` report the observation bound without limiting engine rows. The original `log_level_basic` WriteToLog configuration was verified to appear in real events with its marker and redacted credential text; ERROR events remain visible.

Every event includes `seq` (monotonic within a run), `type`, and `time` (Unix milliseconds):

```json
{"seq":4,"type":"row","time":0,"node":"native-script","copy":0,"direction":"written","rowNumber":1,"fields":{"name":"alice","greeting":"ALICE!"},"fieldsMeta":[{"name":"name","type":"String"},{"name":"greeting","type":"String"}],"fieldErrors":[]}
```

Row directions are `read`, `written`, and `error`; values are normalized with the original Kettle value metadata and bounded to 4,096 characters, with null retained. These events are bounded observation samples. Live and final `metrics.nodes` use native counters `{node,copy,status,read,written,input,output,rejected,errors}`. The listener is attached after `prepareExecution` and before `startThreads`, so fast input cannot finish before observation begins.

State events report `PREPARING`, `RUNNING` and `STOPPING`. Terminal states are `SUCCEEDED`, `FAILED`, `STOPPED`, `PREVIEW_COMPLETE` and `TIMED_OUT`. Native `Trans.getErrors()` takes precedence over `Result.getResult()`; the latter is retained as diagnostic `resultBoolean` and never used alone to declare success. Preview truncation is explicitly reported. JVM failures without an engine terminal event produce a broker terminal event. Run snapshots include final native counters, exit code, creation/finish Unix seconds, event count and private output inventory.

## Verified scope and remaining work

Twenty-one focused acceptance tests passed on 2026-09-10, including the separately compiled native Job executor. They cover original CSV file input → original modified-JavaScript step → original text-file output with exact Chinese/ASCII file readback; per-node rows/fields/counters; actual upstream metadata excluding disconnected nodes; preview with no downstream file; stopping an active original Delay step; original script failure with nonzero errors; 350-row execution despite two-row event sampling; immutable/idempotent launch; XML guards; OS file/network denial without SecurityManager; trusted policy permissions and host restrictions; safe UTF-8 basenames and macOS filename collision refusal; automatic Kafka preview overrides without changing saved/original XML; native BASIC logging and redaction; native Job validation/execution through the broker; and real HTTP bearer rejection, browser Origin rejection, Chinese binary upload, output byte/hash download and typed events. Four additional real broker restart tests cover durable recovery.

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

## Linux broker dispatch

The dispatcher requires the separate `kettle_linux_runtime.py` adapter plus its assets, including the identity/ownership API from its `d0ee4a8` update. See [the Linux adapter guide](DATA_GOVERNANCE_KETTLE_LINUX.md) for the pinned image, network policy, ownership checks and deployment prerequisites. This integration has only mock coverage for Linux. No Docker daemon, container, iptables, nsenter, server 250 or root broker was operated during the dispatcher implementation; Linux host acceptance remains required.

Platform behavior is explicit:

| Platform/configuration | Behavior |
| --- | --- |
| macOS, no Linux config | Existing Seatbelt launcher and exact localhost grants |
| macOS with Linux config | Rejected; configuration cannot silently change isolation mode |
| Linux without private Linux config | Fails closed before any process launch |
| Linux with `execution_enabled=false` | Can inspect/recover existing owned records; rejects new execution |
| Linux with reviewed, enabled config | Delegates launch to `LinuxRuntime`; no `sandbox-exec` or host Java fallback |

Linux configuration is a broker-owned mode-600 file. Only its `endpoints` provide network grants; macOS `--network-policy`, `--allow-endpoint` and `--ftp-test-policy` flags cannot be mixed into Linux execution. Health reports `sandbox:"linux-docker"`, the effective registered endpoints and `runtimePolicy.executionEnabled`. Capability discovery, transformation `load` used by save, and Job validation use network-none. Explicit transformation `validate` uses only the configured trusted endpoints. The broker compares every adapter plan’s endpoint set with the operation policy before launch and rejects an old or mismatched adapter.

Linux timezone support requires the matching timezone-aware adapter. Before launching any container the dispatcher checks its offline plan against the frozen XML timezone; an old UTC-only or mismatched plan is refused. After launch it verifies the container identity's timezone and artifact/timezone sourceHash against that plan. There is no arbitrary extra-argv option. The adapter owns the actual `-Duser.timezone` argument and container labels; the broker does not infer a timezone from the Linux host.

Four directories must be separate and non-overlapping: artifact `worker_root`, `operations_root`, adapter `state_root`, and the broker's `--runtime` holding definitions, token and `run-records`. The manifest is read from `worker_root`, not from the broker state directory. The adapter mounts only classes/lib read-only and that one operation at `/work/<runId>`; neither controller journal is mounted into the original engine. Container uid/gid access to artifacts must be provisioned deliberately. `stage_run_owner` is opt-in and, when allowed by the adapter, affects only one reserved run, never shared artifacts or controller state.

The adapter returns a Popen-shaped stream handle, but its PID is merely the attach CLI. The broker records and verifies `containerId`, `sourceHash` and the intent nonce instead. A completed restart calls the adapter's read-only status and identity APIs. Incomplete or inconsistent records call its journal recovery and remain `RECOVERY_REQUIRED`, with no recreation or automatic replay. If container launch loses acknowledgement, or the container exits without a witnessed native terminal event, the broker also preserves `RECOVERY_REQUIRED`. Stop uses `request_stop(..., expected_nonce=...)`; force-stop is scoped to the verified container identity and never sends a signal to a saved host PID.

Example preparation and startup on a separately reviewed Linux host, from the integrated clean source tree:

```sh
# Run preparation as the configured container service uid, with pre-provisioned private directories.
python3 tools/data-governance/kettle_worker.py prepare \
  --archive /srv/rynew-kettle/input/kettle6.1.3.0.zip \
  --runtime /srv/rynew-kettle/artifacts/verified-version \
  --java-home /usr/lib/jvm/java-17-openjdk-amd64

# Prepare/review mode-600 linux.json from linux/config.example.json; keep execution disabled initially.
# This is an offline plan, not a container launch.
python3 data-governance/kettle-worker/linux/smoke.py \
  --config /srv/rynew-kettle/private/linux.json

# After the host/image/uid/network plan has been reviewed and execution deliberately enabled:
python3 tools/data-governance/kettle_worker.py serve \
  --runtime /srv/rynew-kettle/broker \
  --linux-config /srv/rynew-kettle/private/linux.json \
  --port 19162 --timeout 120
```

The Java home and service paths above are examples to verify on the actual host. Use distinct artifact, operation and controller paths in the config; do not reuse the Mac localhost PG/FTP addresses inside a container. Image preparation, service exposure and permissions are separate reviewed deployment steps. No package installation, image pull, global permission change or production deployment is performed by this broker command.

Eleven dispatcher-only mock tests cover absent/untrusted/disabled configuration, directory separation, the Linux launch boundary, verified container identity, read-only completed restoration, incomplete recovery, nonce-checked stop, rejection of changed container identity, ambiguous/unobserved completion without replay, and timezone-plan mismatch before container launch. They prohibit host Popen and bare PID signalling. The existing macOS native, recovery and metadata test suites remain required regression checks:

```sh
python3 data-governance/kettle-worker/tests/test_kettle_worker_linux_dispatch.py
```

The field-diagnostic follow-up is verified in a separate proof runtime, leaving the shared integration runtime untouched: native CSV lazy replacement reports the precise conversion failure; native integer fields with format `0` retain `9007199254740993` and `9223372036854775807`; blocked PostgreSQL metadata queries report field diagnostics while offline save succeeds without those queries. Dispatcher mocks cover trusted field-query endpoints, offline save and refusal of mismatched adapter policies.

The trusted PostgreSQL proof also performs native offline save, explicitly discovers `name,n` through the registered isolated PostgreSQL endpoint, then runs the original TableInput → ScriptValueMod → SelectValues → TextFileOutput chain and reads back the exact two-row result. Only a read-only VALUES query is used; no fixture table or role is modified. Evidence is kept under the separate `data-governance-kettle-field-diagnostics-v2` runtime.
