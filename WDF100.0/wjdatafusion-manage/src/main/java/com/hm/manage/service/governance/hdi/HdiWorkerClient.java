package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Private ordered JSON-lines transport. A broken connection is never restarted or replayed automatically. */
@Component
public class HdiWorkerClient implements AutoCloseable {
    public static final class Failure extends RuntimeException {
        private final boolean uncertain;
        private final String code;
        Failure(String code, boolean uncertain) { super("原 Core 执行通道不可用或结果待核查"); this.code = code; this.uncertain = uncertain; }
        public boolean uncertain() { return uncertain; }
        public String code() { return code; }
    }
    private static final int REQUEST_LIMIT = 48 * 1024 * 1024, RESPONSE_LIMIT = 8 * 1024 * 1024;
    private final HdiCoreProperties properties;
    private final Duration timeout;
    private final Duration startupTimeout;
    private final BlockingQueue<Object> responses = new ArrayBlockingQueue<>(4);
    private final ExecutorService pipes = Executors.newFixedThreadPool(3, task -> { Thread thread = new Thread(task, "hdi-worker-pipe"); thread.setDaemon(true); return thread; });
    private Process process;
    private boolean broken, closed;
    private JsonNode greeting;

    @Autowired public HdiWorkerClient(HdiCoreProperties properties) { this(properties, Duration.ofSeconds(30)); }
    HdiWorkerClient(HdiCoreProperties properties, Duration timeout) {
        this(properties, timeout, Duration.ofSeconds(properties.getWorkerStartupTimeoutSeconds()));
    }
    HdiWorkerClient(HdiCoreProperties properties, Duration timeout, Duration startupTimeout) {
        this.properties = properties; this.timeout = timeout; this.startupTimeout = startupTimeout;
    }

    public synchronized JsonNode hello() {
        boolean existing = process != null;
        ensureStarted();
        if (existing) greeting = checkedGreeting(exchange("hello", HdiProtocol.JSON.createObjectNode()));
        return greeting.deepCopy();
    }
    public synchronized JsonNode reserve(HdiTaskStore.Run run) {
        ObjectNode body = owned(run); body.put("businessTaskId", Long.toString(run.taskId())).put("revision", Long.toString(run.revision()))
            .put("nativeFlowId", Long.toString(run.nativeFlowId())).put("nativeInstanceId", Long.toString(run.nativeInstanceId()))
            .put("execId", run.execId()).put("workspaceId", Integer.toString(run.workspaceId())).put("metadataIdBase", Long.toString(run.metadataIdBase()))
            .put("mode", run.mode().equals("DEBUG") ? "test" : "run");
        return bound(run, request("reserve", body));
    }
    public synchronized JsonNode submit(HdiTaskStore.Run run, JsonNode compiled) {
        byte[] bytes;
        try { bytes = HdiProtocol.JSON.writeValueAsBytes(compiled); } catch (IOException error) { throw new Failure("INVALID_COMPILED", false); }
        if (bytes.length > 32 * 1024 * 1024) throw new Failure("COMPILED_LIMIT", false);
        ObjectNode body = owned(run).put("compiledBase64", Base64.getEncoder().encodeToString(bytes)).put("sha256", HdiTaskStore.sha256(bytes));
        return bound(run, request("submit", body));
    }
    public synchronized JsonNode status(HdiTaskStore.Run run) { return bound(run, request("status", owned(run))); }
    public synchronized JsonNode cancel(HdiTaskStore.Run run) { return bound(run, request("cancel", owned(run))); }
    public synchronized JsonNode logs(HdiTaskStore.Run run, long cursor, int maxBytes) {
        if (cursor < 0 || maxBytes < 4 || maxBytes > 65536) throw new Failure("INVALID_CURSOR", false);
        JsonNode value = request("logs", owned(run).put("cursor", cursor).put("maxBytes", maxBytes));
        if (!value.path("text").isTextual() || !value.path("cursor").isIntegralNumber() || value.path("cursor").longValue() != cursor
            || !value.path("nextCursor").isIntegralNumber() || value.path("nextCursor").longValue() < cursor || !value.path("eof").isBoolean()) throw failed("INVALID_LOG_RESPONSE");
        return value;
    }
    public synchronized JsonNode artifact(HdiTaskStore.Run run, String artifactId, long offset, int maxBytes) {
        if (artifactId == null || !artifactId.matches("[A-Za-z0-9_-]{1,128}") || offset < 0 || maxBytes < 1 || maxBytes > 1024 * 1024) throw new Failure("INVALID_ARTIFACT_REQUEST", false);
        return request("artifact", owned(run).put("artifactId", artifactId).put("offset", offset).put("maxBytes", maxBytes));
    }
    public synchronized JsonNode shutdown() {
        if (process == null || closed) return HdiProtocol.JSON.createObjectNode().put("stopped", true);
        return request("shutdown", HdiProtocol.JSON.createObjectNode());
    }
    private static ObjectNode owned(HdiTaskStore.Run run) {
        return HdiProtocol.JSON.createObjectNode().put("ownerId", Long.toString(run.ownerId())).put("runId", run.id());
    }
    private JsonNode bound(HdiTaskStore.Run run, JsonNode view) {
        for (Map.Entry<String,String> entry : Map.of("runId", run.id(), "ownerId", Long.toString(run.ownerId()), "businessTaskId", Long.toString(run.taskId()),
            "revision", Long.toString(run.revision()), "nativeFlowId", Long.toString(run.nativeFlowId()), "nativeInstanceId", Long.toString(run.nativeInstanceId()),
            "workspaceId", Integer.toString(run.workspaceId()), "execId", run.execId(), "metadataIdBase", Long.toString(run.metadataIdBase())).entrySet()) {
            if (!view.path(entry.getKey()).isTextual() || !entry.getValue().equals(view.path(entry.getKey()).textValue())) throw failed("RUN_BINDING_MISMATCH");
        }
        if (!view.path("state").isTextual() || !view.path("finalized").isBoolean()) throw failed("INVALID_RUN_VIEW");
        return view;
    }
    private JsonNode request(String operation, ObjectNode body) {
        ensureStarted(); return exchange(operation, body);
    }
    private void ensureStarted() {
        if (closed || broken) throw new Failure("CHANNEL_DISCONNECTED", true);
        if (process != null) {
            if (!process.isAlive()) throw failed("WORKER_EXITED");
            return;
        }
        if (!properties.isEnabled() || properties.getWorkerCommand().isEmpty()) throw new Failure("WORKER_NOT_CONFIGURED", false);
        try {
            List<String> command = properties.getWorkerCommand();
            if (command.stream().anyMatch(value -> value == null || value.isBlank() || value.indexOf('\0') >= 0)) throw new IOException("command");
            Path log = logFile(); ProcessBuilder builder = new ProcessBuilder(command).directory(log.getParent().toFile());
            for (String key : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH", "DYLD_INSERT_LIBRARIES")) builder.environment().remove(key);
            process = builder.start(); Process owned = process;
            pipes.submit(() -> readResponses(owned.getInputStream()));
            pipes.submit(() -> drainLog(owned.getErrorStream(), log));
            // Hash verification, namespace probes and bundled ZooKeeper startup precede the first reply.
            // A cold start has its own bounded budget; later requests retain the normal deadline.
            greeting = checkedGreeting(exchange("hello", HdiProtocol.JSON.createObjectNode(), startupTimeout));
        } catch (Failure failure) { throw failure; }
        catch (IOException error) { broken = true; throw new Failure("WORKER_START_FAILED", process != null); }
    }
    private JsonNode checkedGreeting(JsonNode value) {
        Set<String> required = Set.of("reserve", "submit", "status", "cancel", "logs", "artifact", "shutdown");
        var capabilities = new java.util.HashSet<String>(); value.path("capabilities").forEach(item -> capabilities.add(item.asText()));
        if (!"original-detl".equals(value.path("engine").asText()) || !value.path("ready").asBoolean(false)
            || !capabilities.containsAll(required) || !value.path("binding").path("id").isTextual() || !value.path("binding").path("name").isTextual()) throw failed("WORKER_NOT_READY");
        if (value.has("capacity")) {
            JsonNode capacity = value.path("capacity");
            if (!capacity.path("limit").isInt() || capacity.path("limit").intValue() < 1 || !capacity.path("active").isInt() || capacity.path("active").intValue() < 0
                || !capacity.path("available").isInt() || capacity.path("available").intValue() != Math.max(0, capacity.path("limit").intValue()-capacity.path("active").intValue())) throw failed("INVALID_CAPACITY");
        }
        return value;
    }
    private JsonNode exchange(String operation, ObjectNode body) {
        return exchange(operation, body, operation.equals("shutdown") ? Duration.ofSeconds(80) : timeout);
    }
    private JsonNode exchange(String operation, ObjectNode body, Duration requestTimeout) {
        if (closed || broken || process == null) throw new Failure("CHANNEL_DISCONNECTED", true);
        String requestId = UUID.randomUUID().toString(); body = body.deepCopy();
        body.put("protocolVersion", 1).put("requestId", requestId).put("op", operation);
        try {
            byte[] data = HdiProtocol.JSON.writeValueAsBytes(body);
            if (data.length > REQUEST_LIMIT) throw new Failure("REQUEST_LIMIT", false);
            final byte[] payload = data;
            Future<?> write = pipes.submit(() -> { try { process.getOutputStream().write(payload); process.getOutputStream().write('\n'); process.getOutputStream().flush(); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); } });
            long deadline = System.nanoTime() + requestTimeout.toNanos();
            write.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
            Object received = responses.poll(Math.max(1, deadline-System.nanoTime()), TimeUnit.NANOSECONDS);
            if (!(received instanceof byte[] bytes)) throw failed(received == null ? "WORKER_TIMEOUT" : "WORKER_DISCONNECTED");
            JsonNode result = HdiProtocol.JSON.readTree(bytes);
            if (result == null || !result.isObject() || !result.path("protocolVersion").isInt() || result.path("protocolVersion").intValue() != 1
                || !requestId.equals(result.path("requestId").asText()) || !result.path("ok").isBoolean()) throw failed("INVALID_WORKER_RESPONSE");
            if (!result.path("ok").asBoolean()) {
                String code = result.path("error").path("code").asText();
                if (!code.matches("[A-Z0-9_]{1,80}")) throw failed("INVALID_WORKER_ERROR");
                throw new Failure(code, false);
            }
            if (!result.path("data").isObject()) throw failed("INVALID_WORKER_DATA");
            return result.path("data");
        } catch (Failure failure) { throw failure; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw failed("WORKER_INTERRUPTED"); }
        catch (Exception error) { throw failed("WORKER_TRANSPORT_FAILED"); }
    }
    private Failure failed(String code) {
        broken = true;
        // EOF is the worker's documented controlled-stop signal. Never start another worker or replay a request.
        if (process != null) closeInputAsync(process);
        return new Failure(code, true);
    }
    private void readResponses(InputStream source) {
        try (InputStream input = source) {
            ByteArrayOutputStream line = new ByteArrayOutputStream(); int value;
            while ((value = input.read()) != -1) {
                if (value == '\n') { if (!responses.offer(line.toByteArray())) throw new IOException("unsolicited response overflow"); line.reset(); }
                else { if (line.size() >= RESPONSE_LIMIT) throw new IOException("response limit"); line.write(value); }
            }
        } catch (IOException ignored) { }
        finally { responses.offer(new Object()); }
    }
    private Path logFile() throws IOException {
        if (properties.getStateDir() == null || properties.getStateDir().isBlank()) throw new IOException("state required");
        Path root = Path.of(properties.getStateDir()); if (!root.isAbsolute() || !root.equals(root.normalize())) throw new IOException("state path");
        for (Path path = root; path != null; path = path.getParent()) if (Files.isSymbolicLink(path)) throw new IOException("symbolic state");
        if (!Files.exists(root)) Files.createDirectories(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var attributes = Files.readAttributes(root, java.nio.file.attribute.PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || !attributes.owner().getName().equals(System.getProperty("user.name")) || !attributes.permissions().equals(PosixFilePermissions.fromString("rwx------"))) throw new IOException("private state");
        Path log = root.resolve("worker-" + UUID.randomUUID() + ".stderr");
        Files.createFile(log, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); return log;
    }
    private static void drainLog(InputStream source, Path log) {
        try (InputStream input = source; OutputStream output = Files.newOutputStream(log, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            byte[] block = new byte[8192]; int count, remaining = 65536;
            while ((count = input.read(block)) != -1) { int write = Math.min(count, remaining); if (write > 0) output.write(block, 0, write); remaining -= write; }
        } catch (IOException ignored) { }
    }
    private static void closeInputAsync(Process owned) {
        // A writer may be blocked in a full OS pipe; closing on the request thread can defeat its deadline.
        Thread closer = new Thread(() -> { try { owned.getOutputStream().close(); } catch (IOException ignored) { } }, "hdi-worker-eof");
        closer.setDaemon(true); closer.start();
    }
    @PreDestroy @Override public synchronized void close() {
        if (closed) return;
        try { if (process != null && !broken && process.isAlive()) shutdown(); }
        catch (Failure ignored) { /* Caller persists UNKNOWN; no successful-shutdown claim. */ }
        finally {
            closed = true;
            if (process != null) {
                closeInputAsync(process);
                try { process.waitFor(10, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
            pipes.shutdownNow();
        }
    }
}
