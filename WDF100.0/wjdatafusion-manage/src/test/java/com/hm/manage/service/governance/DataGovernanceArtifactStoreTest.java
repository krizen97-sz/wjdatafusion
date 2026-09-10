package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DataGovernanceArtifactStoreTest
{
    @TempDir Path temporary;
    private DataGovernanceProperties properties()
    { var config = new DataGovernanceProperties(); config.setStorageDir(temporary.resolve("state").toString()); return config; }
    private StoredRun run()
    {
        StoredRun stored = new StoredRun(); stored.ownerId = 7; stored.run = new TestRun();
        stored.run.id = UUID.randomUUID().toString(); stored.run.definitionHash = "a".repeat(64); stored.run.status = "RUNNING";
        return stored;
    }
    private void success(StoredRun stored) { stored.run.status = "SUCCEEDED"; stored.run.cleanupConfirmed = true; }
    private Path runPath(StoredRun stored) { return temporary.resolve("state/artifacts").resolve(stored.run.id); }

    @Test void keepsCompleteBinaryAcrossReopenWithPrivatePermissionsAndImmutableManifest() throws Exception
    {
        var stored = run(); var store = new DataGovernanceArtifactStore(properties()); var stage = store.begin(stored);
        byte[] bytes = new byte[24001]; for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        var artifact = store.capture(stage, "完整产物.bin", "application/octet-stream", bytes.length,
            output -> { output.write(bytes, 0, 10000); output.write(bytes, 10000, bytes.length - 10000); });
        success(stored); var manifest = store.publish(stage, stored);
        assertTrue(stored.run.artifactsManifestAvailable); assertEquals(1, stored.run.artifactCount);
        assertEquals(bytes.length, artifact.byteSize()); assertEquals(1, artifact.order());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), artifact.sha256());
        var reopened = new DataGovernanceArtifactStore(properties());
        assertEquals(manifest, reopened.manifest(stored.run.id, 7));
        try (var input = reopened.read(stored.run.id, artifact.id(), 7))
        { assertArrayEquals(bytes, input.readAllBytes()); assertEquals(-1, input.read()); assertEquals(-1, input.read()); }
        assertThrows(ServiceException.class, () -> store.begin(stored)); store.abandon(stage);
        assertEquals(manifest, reopened.manifest(stored.run.id, 7));
        if (temporary.getFileSystem().supportedFileAttributeViews().contains("posix"))
        {
            for (Path path : List.of(runPath(stored).getParent(), runPath(stored), runPath(stored).resolve("content")))
                assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(path));
            for (Path path : List.of(runPath(stored).resolve("manifest.json"), runPath(stored).resolve("owner.json"), runPath(stored).resolve("content/" + artifact.id() + ".bin")))
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path));
        }
    }

    @Test void onlySuccessfulCleanedRunsCanPublishAndFailedStagingIsNotDeliverable() throws Exception
    {
        var store = new DataGovernanceArtifactStore(properties());
        for (String state : List.of("RUNNING", "FAILED", "CANCELLED", "TIMED_OUT", "EMPTY", "CLEANUP_REQUIRED", "SUCCEEDED"))
        {
            var stored = run(); var stage = store.begin(stored);
            store.capture(stage, "result.bin", "application/octet-stream", 3, output -> output.write(new byte[]{1, 2, 3}));
            stored.run.status = state; stored.run.cleanupConfirmed = !state.equals("SUCCEEDED");
            assertThrows(ServiceException.class, () -> store.publish(stage, stored), state);
            assertFalse(stored.run.artifactsManifestAvailable);
            assertThrows(ServiceException.class, () -> store.manifest(stored.run.id, 7));
            store.abandon(stage);
            assertFalse(Files.exists(runPath(stored).resolve("staging")));
        }
    }

    @Test void streamFailureOrLengthMismatchPoisonsStageInsteadOfPublishingPartialFiles()
    {
        var store = new DataGovernanceArtifactStore(properties()); var stored = run(); var stage = store.begin(stored);
        store.capture(stage, "first", "application/octet-stream", 1, output -> output.write(1));
        assertThrows(ServiceException.class, () -> store.capture(stage, "short", "application/octet-stream", 20, output -> output.write(new byte[3])));
        success(stored); assertThrows(ServiceException.class, () -> store.publish(stage, stored));
        store.abandon(stage); assertFalse(Files.exists(runPath(stored).resolve("staging")));
        var interrupted = run(); var other = store.begin(interrupted);
        assertThrows(ServiceException.class, () -> store.capture(other, "interrupted", "application/octet-stream", 20,
            output -> { output.write(1); throw new IOException("simulated transport failure"); }));
        success(interrupted); assertThrows(ServiceException.class, () -> store.publish(other, interrupted)); store.abandon(other);
    }

    @Test void enforcesCountAndDeclaredAndActualSizeBeforeAnythingBecomesDeliverable()
    {
        var store = new DataGovernanceArtifactStore(properties()); var tooLarge = run(); var largeStage = store.begin(tooLarge);
        AtomicBoolean read = new AtomicBoolean();
        assertThrows(ServiceException.class, () -> store.capture(largeStage, "too-large", "application/octet-stream", DataGovernanceArtifactStore.MAX_ARTIFACT_BYTES + 1,
            output -> read.set(true))); assertFalse(read.get()); store.abandon(largeStage);
        var actual = run(); var actualStage = store.begin(actual);
        assertThrows(ServiceException.class, () -> store.capture(actualStage, "actual", "application/octet-stream", 1, output -> output.write(new byte[2])));
        store.abandon(actualStage);
        var many = run(); var stage = store.begin(many);
        for (int i = 0; i < 100; i++) store.capture(stage, "empty-" + i, "application/octet-stream", 0, output -> { });
        assertThrows(ServiceException.class, () -> store.capture(stage, "excess", "application/octet-stream", 0, output -> { }));
        success(many); assertThrows(ServiceException.class, () -> store.publish(stage, many)); store.abandon(stage);
    }

    @Test void totalByteLimitAccepts32MiBButRejectsTheNextByte()
    {
        var store = new DataGovernanceArtifactStore(properties()); var stored = run(); var stage = store.begin(stored);
        byte[] block = new byte[8192];
        for (int i = 0; i < 4; i++) store.capture(stage, "part-" + i, "application/octet-stream", DataGovernanceArtifactStore.MAX_ARTIFACT_BYTES,
            output -> { for (int n = 0; n < 1024; n++) output.write(block); });
        assertThrows(ServiceException.class, () -> store.capture(stage, "extra", "application/octet-stream", 1, output -> output.write(1)));
        success(stored); assertThrows(ServiceException.class, () -> store.publish(stage, stored)); store.abandon(stage);
    }

    @Test void requiresOwnershipDetectsTamperingAndRejectsEarlyClose() throws Exception
    {
        var store = new DataGovernanceArtifactStore(properties()); var stored = run(); var stage = store.begin(stored);
        var artifact = store.capture(stage, "result", "application/octet-stream", 3, output -> output.write(new byte[]{1, 2, 3}));
        success(stored); store.publish(stage, stored);
        assertThrows(ServiceException.class, () -> store.manifest(stored.run.id, 8));
        assertThrows(ServiceException.class, () -> store.read(stored.run.id, artifact.id(), 8));
        assertThrows(ServiceException.class, () -> store.read(stored.run.id, "../../outside", 7));
        var input = store.read(stored.run.id, artifact.id(), 7); assertEquals(1, input.read());
        assertThrows(IOException.class, input::close);
        Files.write(runPath(stored).resolve("content/" + artifact.id() + ".bin"), new byte[]{9, 8, 7});
        assertThrows(ServiceException.class, () -> store.read(stored.run.id, artifact.id(), 7));
    }

    @Test void rejectsSymbolicLinksAndNonBasenameMetadata() throws Exception
    {
        var store = new DataGovernanceArtifactStore(properties()); var invalid = run(); var badStage = store.begin(invalid);
        assertThrows(ServiceException.class, () -> store.capture(badStage, "../escape", "text/plain", 0, output -> { })); store.abandon(badStage);
        var stored = run(); var stage = store.begin(stored); var artifact = store.capture(stage, "result", "text/plain", 3, output -> output.write(new byte[]{1, 2, 3}));
        success(stored); store.publish(stage, stored);
        Path file = runPath(stored).resolve("content/" + artifact.id() + ".bin"); Path other = temporary.resolve("outside"); Files.write(other, new byte[]{1, 2, 3});
        Files.delete(file); Files.createSymbolicLink(file, other);
        assertThrows(ServiceException.class, () -> store.read(stored.run.id, artifact.id(), 7));
        var linkedRun = run(); Files.createSymbolicLink(runPath(linkedRun), runPath(stored));
        assertThrows(ServiceException.class, () -> store.begin(linkedRun));
        assertThrows(ServiceException.class, () -> store.manifest(linkedRun.run.id, 7));
    }
}
