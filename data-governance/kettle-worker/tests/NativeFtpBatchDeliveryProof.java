import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.pentaho.di.core.Result;
import org.pentaho.di.core.logging.LogLevel;
import org.pentaho.di.job.Job;
import org.pentaho.di.job.entries.ftpput.JobEntryFTPPUT;

/** Synthetic-only native helper harness. Never loads or executes a user transformation or job. */
public final class NativeFtpBatchDeliveryProof {
    public static void main(String[] arguments) throws Exception {
        var wire = KettleWorker.wire; System.setOut(System.err);
        Path root = Path.of(arguments[0]); KettleWorker.root = root; KettleWorker.init();
        Properties fixture = new Properties();
        try (var input = Files.newInputStream(root.resolve("fixture.properties"))) { fixture.load(input); }
        String mode = fixture.getProperty("mode");
        Job job = new Job(); job.setName("Synthetic FTP batch proof"); job.setLogLevel(LogLevel.BASIC);
        JobEntryFTPPUT entry = new JobEntryFTPPUT("Original FTP batch " + mode); entry.setParentJob(job);
        entry.setServerName("127.0.0.1"); entry.setServerPort(fixture.getProperty("port"));
        entry.setUserName(fixture.getProperty("username")); entry.setPassword(fixture.getProperty("password"));
        entry.setLocalDirectory(root.resolve("output").toString()); entry.setRemoteDirectory(mode);
        entry.setWildcard(".*\\.csv"); entry.setBinaryMode(true); entry.setActiveConnection(false); entry.setTimeout(10000);
        entry.setControlEncoding("UTF-8"); entry.setRemove(mode.equals("remove") || mode.equals("changed"));
        entry.setOnlyPuttingNewFiles(mode.equals("onlynew")); entry.setRename(mode.equals("rename")); entry.setRenameSuffix("uploading");
        KettleWorker.secrets.add(fixture.getProperty("password"));
        String original = entry.getXML(); AtomicBoolean stopped = new AtomicBoolean();
        NativeFtpBatchDelivery.Context context;
        try { context = NativeFtpBatchDelivery.prepare(root, root.resolve("output"), entry, job, stopped::get); }
        catch (Exception error) {
            error.printStackTrace(System.err);
            KettleWorker.event("proof", Map.of("mode", mode, "preparationRejected", true, "errorClass", error.getClass().getName(), "originalEntryRestored", original.equals(entry.getXML())));
            return;
        }
        Result first = entry.execute(new Result(), 0);
        if (mode.equals("stop")) { stopped.set(true); job.stopAll(); }
        if (mode.equals("changed")) Files.writeString(root.resolve("output/file-000.csv"), "CHANGED_AFTER_NATIVE_TRANSFER\n", StandardCharsets.UTF_8);
        Result result = NativeFtpBatchDelivery.complete(context, first, 0);
        Map<String, Object> proof = new LinkedHashMap<>(context.summary());
        proof.put("mode", mode); proof.put("resultBoolean", result.getResult()); proof.put("errors", result.getNrErrors());
        proof.put("stopped", result.isStopped()); proof.put("nativeFileCounter", result.getNrFilesRetrieved());
        proof.put("sameResultObject", result == first); proof.put("originalEntryRestored", original.equals(entry.getXML()));
        KettleWorker.event("proof", proof);
        try { NativeFtpBatchDelivery.complete(context, result, 0); throw new AssertionError("Context was replayable"); }
        catch (IllegalStateException expected) { }
    }
}
