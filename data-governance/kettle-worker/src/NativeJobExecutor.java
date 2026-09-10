import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.pentaho.di.core.Result;
import org.pentaho.di.core.logging.KettleLogStore;
import org.pentaho.di.core.logging.KettleLoggingEventListener;
import org.pentaho.di.core.logging.LogLevel;
import org.pentaho.di.core.logging.LogMessage;
import org.pentaho.di.core.plugins.JobEntryPluginType;
import org.pentaho.di.job.DelegationListener;
import org.pentaho.di.job.Job;
import org.pentaho.di.job.JobEntryListener;
import org.pentaho.di.job.JobExecutionConfiguration;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.job.entries.trans.JobEntryTrans;
import org.pentaho.di.job.entry.JobEntryCopy;
import org.pentaho.di.job.entry.JobEntryInterface;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransAdapter;
import org.pentaho.di.trans.TransExecutionConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Original Job/JobEntry execution. The broker must apply its OS sandbox before calling this class. */
public final class NativeJobExecutor {
    private static final Set<String> ENTRY_IDS = Set.of("SPECIAL", "TRANS", "FTP_PUT");
    private static final int MAX_LOG_EVENTS = 500;

    private NativeJobExecutor() { }

    private record Loaded(Path root, JobMeta meta, List<Map<String, Object>> entries, List<String> secrets) { }

    public static void validate(Path operationRoot) throws Exception {
        Loaded loaded = load(operationRoot);
        List<Map<String, Object>> hops = new ArrayList<>();
        for (var hop : loaded.meta().getJobhops()) {
            if (hop.getFromEntry() == null || hop.getToEntry() == null) throw new IllegalArgumentException("Invalid job hop");
            hops.add(Map.of("from", hop.getFromEntry().getName(), "to", hop.getToEntry().getName(),
                "enabled", hop.isEnabled(), "unconditional", hop.isUnconditional(), "evaluation", hop.getEvaluation()));
        }
        KettleWorker.event("validation", Map.of("valid", true, "name", loaded.meta().getName(),
            "kind", "job", "nodes", loaded.entries(), "hops", hops));
    }

    private static Loaded load(Path operationRoot) throws Exception {
        Path root = operationRoot.toRealPath();
        Document document;
        try (InputStream input = Files.newInputStream(ownedFile(root, root.resolve("transformation.kjb")))) {
            document = KettleWorker.xml(input);
        }
        Element jobXml = document.getDocumentElement();
        if (!"job".equals(jobXml.getTagName())) throw new IllegalArgumentException("Expected job XML");
        registerEntries();
        List<String> secrets = new ArrayList<>();
        collectSecrets(jobXml, secrets);
        validateXml(jobXml);
        JobMeta meta = new JobMeta(jobXml, null, null);
        meta.setFilename(root.resolve("transformation.kjb").toString());
        meta.setVariable("JOB_DIR", root.toString());
        meta.setVariable("WORK_DIR", root.resolve("output").toString());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (JobEntryCopy copy : meta.getJobCopies()) {
            JobEntryInterface entry = copy.getEntry();
            if (entry == null || !ENTRY_IDS.contains(entry.getPluginId())) {
                throw new IllegalArgumentException("Unsupported native job entry");
            }
            entries.add(Map.of("node", copy.getName(), "copy", copy.getNr(),
                "pluginId", entry.getPluginId(), "className", entry.getClass().getName(),
                "classSource", KettleWorker.source(entry.getClass())));
            if (entry instanceof JobEntryTrans transEntry) {
                String filename = meta.environmentSubstitute(transEntry.getFilename());
                Path child = Path.of(filename);
                if (!child.isAbsolute()) child = root.resolve(child);
                child = ownedFile(root, child);
                transEntry.setFileName(child.toString());
                // The original loader will read this again. Reject external entities before it does so.
                try (InputStream input = Files.newInputStream(child)) {
                    Document childXml = KettleWorker.xml(input);
                    if (!"transformation".equals(childXml.getDocumentElement().getTagName())) {
                        throw new IllegalArgumentException("Expected child transformation XML");
                    }
                    collectSecrets(childXml.getDocumentElement(), secrets);
                }
            }
        }
        if (meta.findStart() == null) throw new IllegalArgumentException("Job needs a START entry");
        return new Loaded(root, meta, entries, secrets);
    }

    public static void run(Path operationRoot) throws Exception {
        Loaded loaded = load(operationRoot);
        Path root = loaded.root(); JobMeta meta = loaded.meta();
        List<Map<String, Object>> entries = loaded.entries(); List<String> secrets = loaded.secrets();
        Job job = new Job(null, meta);
        job.setVariable("JOB_DIR", root.toString());
        job.setVariable("WORK_DIR", root.resolve("output").toString());
        job.setLogLevel(LogLevel.BASIC);
        AtomicBoolean requestedStop = new AtomicBoolean();
        AtomicBoolean timedOut = new AtomicBoolean();
        AtomicBoolean uncaughtFailure = new AtomicBoolean();
        AtomicLong observedErrors = new AtomicLong();
        AtomicInteger logCount = new AtomicInteger();
        AtomicInteger entrySequence = new AtomicInteger();
        Map<JobEntryInterface, Integer> executions = Collections.synchronizedMap(new java.util.IdentityHashMap<>());
        List<Trans> children = new CopyOnWriteArrayList<>();

        KettleLoggingEventListener logging = event -> {
            int number = logCount.incrementAndGet();
            if (number > MAX_LOG_EVENTS) return;
            String subject = "", channel = "", message;
            if (event.getMessage() instanceof LogMessage original) {
                subject = original.getSubject(); channel = original.getLogChannelId();
                message = original.toString();
            } else message = String.valueOf(event.getMessage());
            KettleWorker.event("log", Map.of("level", String.valueOf(event.getLevel()),
                "node", subject == null ? "" : redact(subject, secrets),
                "channel", channel == null ? "" : channel,
                "message", redact(message, secrets), "line", number));
        };
        KettleLogStore.getAppender().addLoggingEventListener(logging);
        job.addDelegationListener(new DelegationListener() {
            @Override public void jobDelegationStarted(Job child, JobExecutionConfiguration config) {
                // Nested/remote jobs are not registered by this executor.
                throw new IllegalArgumentException("Nested jobs are not supported by this executor");
            }
            @Override public void transformationDelegationStarted(Trans child, TransExecutionConfiguration config) {
                children.add(child);
                KettleWorker.event("job-transformation", Map.of("state", "PREPARING", "name", child.getName()));
                child.addTransListener(new TransAdapter() {
                    @Override public void transStarted(Trans started) {
                        KettleWorker.event("job-transformation", Map.of("state", "RUNNING", "name", started.getName()));
                    }
                    @Override public void transFinished(Trans finished) {
                        observedErrors.accumulateAndGet(finished.getErrors(), Math::max);
                        KettleWorker.event("job-transformation", Map.of("state",
                            finished.getErrors() > 0 ? "FAILED" : finished.isStopped() ? "STOPPED" : "SUCCEEDED",
                            "name", finished.getName(), "errors", finished.getErrors()));
                    }
                });
                if (requestedStop.get()) child.stopAll();
            }
        });
        job.addJobEntryListener(new JobEntryListener() {
            @Override public void beforeExecution(Job running, JobEntryCopy copy, JobEntryInterface entry) {
                int execution = entrySequence.incrementAndGet(); executions.put(entry, execution);
                KettleWorker.event("job-entry", Map.of("phase", "BEFORE", "node", copy.getName(),
                    "copy", copy.getNr(), "pluginId", entry.getPluginId(), "execution", execution));
            }
            @Override public void afterExecution(Job running, JobEntryCopy copy, JobEntryInterface entry, Result result) {
                boolean originalBoolean = result != null && result.getResult();
                long errors = result == null ? 1 : result.getNrErrors();
                if (entry instanceof JobEntryTrans transEntry && transEntry.getTrans() != null) {
                    errors = Math.max(errors, transEntry.getTrans().getErrors());
                }
                observedErrors.accumulateAndGet(errors, Math::max);
                // Some distributed original classes return true with errors. Hop routing uses this boolean.
                // Correct it before Job chooses a success hop; preserve both values in the event.
                if (result != null && (errors > 0 || requestedStop.get() || result.isStopped())) {
                    result.setNrErrors(errors); result.setResult(false);
                }
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("phase", "AFTER"); fields.put("node", copy.getName()); fields.put("copy", copy.getNr());
                fields.put("pluginId", entry.getPluginId()); fields.put("execution", executions.remove(entry));
                fields.put("errors", errors); fields.put("originalResultBoolean", originalBoolean);
                fields.put("resultBoolean", result != null && result.getResult());
                fields.put("resultCorrected", originalBoolean && result != null && !result.getResult());
                fields.put("files", result == null ? 0 : result.getNrFilesRetrieved());
                fields.put("read", result == null ? 0 : result.getNrLinesRead());
                fields.put("written", result == null ? 0 : result.getNrLinesWritten());
                KettleWorker.event("job-entry", fields);
            }
        });
        job.setUncaughtExceptionHandler((thread, error) -> {
            uncaughtFailure.set(true);
            KettleWorker.event("job-error", Map.of("errorClass", error.getClass().getName()));
        });
        Runnable stop = () -> {
            if (requestedStop.compareAndSet(false, true)) {
                KettleWorker.event("state", Map.of("state", "STOPPING", "reason", timedOut.get() ? "TIMEOUT" : "REQUEST"));
            }
            job.stopAll();
            for (Trans child : children) if (!child.isFinished()) child.stopAll();
        };
        Thread controller = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                for (String line; (line = reader.readLine()) != null;) if ("STOP".equals(line)) stop.run();
            } catch (Exception ignored) { }
        }, "native-job-control");
        controller.setDaemon(true);
        long timeout = Math.max(1, Math.min(900, Long.getLong("governance.job.timeout.seconds", 120)));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(timeout);
        try {
            KettleWorker.event("state", Map.of("state", "PREPARING", "entries", entries));
            job.start(); controller.start();
            KettleWorker.event("state", Map.of("state", "RUNNING"));
            while (job.isAlive()) {
                if (System.nanoTime() >= deadline) { timedOut.set(true); stop.run(); }
                job.join(100);
            }
            // JobEntryTrans has already consumed Trans.waitUntilFinished()'s one-shot queue.
            // Calling it a second time blocks forever in this original distribution.
            // Confirm the native finished flags after the owning Job thread has returned instead.
            long finishDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (children.stream().anyMatch(child -> !child.isFinished()) && System.nanoTime() < finishDeadline) {
                stop.run(); Thread.sleep(50);
            }
            boolean childrenFinished = children.stream().allMatch(Trans::isFinished);
            Result result = job.getResult();
            long errors = Math.max(observedErrors.get(), Math.max(job.getErrors(), result == null ? 1 : result.getNrErrors()));
            boolean failed = errors > 0 || uncaughtFailure.get() || !childrenFinished || result == null || !result.getResult();
            String state = timedOut.get() || !childrenFinished ? "FAILED" : requestedStop.get() ? "STOPPED" : failed ? "FAILED" : "SUCCEEDED";
            KettleWorker.event("terminal", Map.of("state", state, "errors", errors,
                "resultBoolean", result != null && result.getResult(), "jobFinished", job.isFinished(),
                "childrenFinished", childrenFinished, "timedOut", timedOut.get(),
                "entriesExecuted", entrySequence.get(), "logTruncated", logCount.get() > MAX_LOG_EVENTS));
        } finally {
            KettleLogStore.getAppender().removeLoggingEventListener(logging);
        }
    }

    private static void registerEntries() throws Exception {
        KettleWorker.register(org.pentaho.di.core.encryption.TwoWayPasswordEncoderPluginType.class,
            org.pentaho.di.core.encryption.TwoWayPasswordEncoderInterface.class, "Kettle", "Kettle", "Encryption",
            org.pentaho.di.core.encryption.KettleTwoWayPasswordEncoder.class.getName());
        org.pentaho.di.core.encryption.Encr.init("Kettle");
        for (String[] item : new String[][] {
            {"SPECIAL", "special.JobEntrySpecial"}, {"TRANS", "trans.JobEntryTrans"}, {"FTP_PUT", "ftpput.JobEntryFTPPUT"}
        }) KettleWorker.register(JobEntryPluginType.class, JobEntryInterface.class, item[0], item[0], "NativeJob",
            "org.pentaho.di.job.entries." + item[1]);
    }

    private static Path ownedFile(Path root, Path file) throws Exception {
        Path real = file.toRealPath();
        if (!real.startsWith(root) || !Files.isRegularFile(real) || Files.size(real) > 4 * 1024 * 1024) {
            throw new IllegalArgumentException("Job definition must be a bounded file inside its operation directory");
        }
        return real;
    }

    private static String direct(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) return element.getTextContent();
        }
        return "";
    }

    private static void validateXml(Element xml) {
        var entries = xml.getElementsByTagName("entry");
        if (entries.getLength() == 0 || entries.getLength() > 100) throw new IllegalArgumentException("Job needs 1 to 100 entries");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            String type = direct(entry, "type");
            if (!ENTRY_IDS.contains(type)) throw new IllegalArgumentException("Unsupported native job plugin: " + type);
            if ("TRANS".equals(type) && (!direct(entry, "slave_server_name").isBlank()
                || "Y".equalsIgnoreCase(direct(entry, "cluster"))
                || !"filename".equalsIgnoreCase(direct(entry, "specification_method")))) {
                throw new IllegalArgumentException("Only operation-local transformation files are supported");
            }
        }
    }

    private static void collectSecrets(Element element, List<String> secrets) {
        if (element.getTagName().toLowerCase(java.util.Locale.ROOT).contains("password")) {
            String value = element.getTextContent();
            if (!value.isBlank()) {
                secrets.add(value);
                try { secrets.add(org.pentaho.di.core.encryption.Encr.decryptPasswordOptionallyEncrypted(value)); }
                catch (RuntimeException ignored) { }
            }
        }
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) collectSecrets(child, secrets);
        }
    }

    private static String redact(String value, List<String> secrets) {
        String text = value == null ? "" : value;
        for (String secret : secrets) if (secret != null && !secret.isEmpty()) text = text.replace(secret, "[redacted]");
        return text.length() > 8192 ? text.substring(0, 8192) + " [truncated]" : text;
    }
}
