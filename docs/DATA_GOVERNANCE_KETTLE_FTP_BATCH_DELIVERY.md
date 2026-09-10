# Original FTP_PUT batch delivery

The supplied `kettle-6.1.0.7.36.jar` implementation of `JobEntryFTPPUT.executeEx` stops after 80 successful `put` operations (`bipush 80`). Its enclosing `execute(Result,int)` still returns `result=true` and `nrErrors=0`. A directory with 136 matching files therefore leaves 56 files unprocessed after a successful native invocation.

`NativeFtpBatchDelivery` wraps that original entry with finite local batches. It never invokes a transformation, Kafka consumer, or replacement FTP client. It does not change the original jar, XML, plugin type, connection, wildcard, binary mode, remove, rename or only-new options.

The containing executor connects the helper at the original Job listener boundaries:

```java
// beforeExecution, after verifying the configured directory belongs to this run/output:
Context context = NativeFtpBatchDelivery.prepare(root, outputDirectory, ftp, runningJob, requestedStop::get);
contexts.put(ftp, context);

// The original Job now invokes the first staged pass normally.
// afterExecution, before Job decides which outgoing hop to follow:
result = NativeFtpBatchDelivery.complete(contexts.remove(ftp), result, copy.getNr());
```

Use an identity map keyed by the actual original `JobEntryFTPPUT` instance. `prepare` can throw before any FTP connection; abort that entry if it does. `complete` returns the same `Result` object and must run before the existing error/stop success-hop correction. It catches further staging/native failures, sets the final result false, and restores the entry's original local-directory string in `finally`. Never invoke `complete` twice or automatically replay a manifest after interruption. Close an unused context if the containing executor aborts before first execution.

Only regular files directly in the approved output directory are considered, matching the original nonrecursive behavior. The helper applies the original environment-substituted Java regex to basenames, sorts them for stable finite pass membership, and freezes each selected file's size, file identity and SHA-256. Direct subdirectories are ignored by the original entry; they are not flattened. The caller still validates the entire configured output tree.

Each pass copies at most 80 files, with unchanged basename and bytes, into `run/delivery/<random-UUID>/pass-NNNN`. These directories are outside `output`; input assets and spool files do not become output downloads. Copies use no-follow channels, reject symbolic/hard links and special files, and verify source identity and SHA before the native invocation. Linux uses `SecureDirectoryStream` when available. macOS JDK 17 lacks that interface, so it rechecks all directory components and the source directory's file identity around no-follow operations. Both paths still require the worker's OS sandbox. These checks detect file mutation; they are not a new security boundary against malicious code already executing within the same JVM and private run directory.

For `remove=Y`, the helper deletes an original output only after the original plugin has actually removed its staged copy and the original output still has its frozen identity and SHA. A changed source is preserved and the batch fails. With `remove=N`, original outputs remain unchanged. A failed or stopped pass prevents all later passes. Files from an interrupted or failed pass are marked uncertain unless native staged deletion supplies stronger evidence; unstarted files remain pending.

The manifest is written and fsynced before every native invocation and after each pass. It records options, source file hashes, pass membership, original native results, file/source states, pending items and terminal outcome. Its runtime events have type `ftp-batch` (PREPARED, PASS_FINISHED, FINAL) or `ftp-batch-error`. `nativeProcessedFiles` and the helper's `Result.nrFilesRetrieved` increment mean files considered by a successful native pass, including any native only-new decision. Existing transformation row counters are not added again. The helper does not claim a remote content hash or upload count from this counter: `remoteHashVerified` is always false. Separate acceptance tests perform real FTP RETR and compare every hash.

The original archive's `only_new` implementation has an additional custom behavior: `executeEx` checks whether a remote file exists, deletes it, clears its local `exists` flag, and only then tests `onlyPuttingNewFiles`. Consequently this archive can replace remote files even when only-new is selected. The helper preserves the original option and explicitly records `ORIGINAL_PLUGIN_DECISION_CUSTOM_ARCHIVE_MAY_REPLACE_EXISTING`; it cannot promise skip semantics that the original plugin does not implement. The real test preloads three different remote files and verifies that the native option still replaces them.

Run the opt-in acceptance with a dedicated prepared proof runtime and a Python environment containing pyftpdlib:

```bash
KETTLE_WORKER_RUNTIME=/private/independent-proof-runtime \
KETTLE_FTP_PYTHON=/private/ftp-venv/bin/python \
python3 data-governance/kettle-worker/tests/test_kettle_ftp_batch_delivery.py
```

The test creates its own random loopback control port and four passive ports, compiles only into the supplied independent runtime, and stops only its own fixture process. It never connects to an existing FTP service. Cases cover 161 files in 80/80/1 passes with remove Y/N, native only-new, native temporary-file rename, a deliberate second-pass server rejection, stop after the first native invocation, changed-source deletion refusal, and symbolic/hard-link rejection. Successful transfers are read back through FTP and checked against every original SHA-256; source preservation/deletion, unchanged original entry XML, no duplicate transfers, input/spool separation and non-replayable context are asserted.
