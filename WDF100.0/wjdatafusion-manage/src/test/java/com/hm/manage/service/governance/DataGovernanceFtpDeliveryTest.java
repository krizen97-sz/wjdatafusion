package com.hm.manage.service.governance;

import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import com.hm.manage.service.governance.DataGovernanceFtpDelivery.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static com.hm.manage.service.governance.DataGovernanceFtpDelivery.directory;

class DataGovernanceFtpDeliveryTest
{
    @TempDir Path temporary;
    DataGovernanceProperties properties()
    { var p = new DataGovernanceProperties(); p.setStorageDir(temporary.toString()); p.getNifi().setBaseUrl("https://localhost:9443/nifi-api"); return p; }
    static StoredRun published(DataGovernanceArtifactStore artifacts, DataGovernanceProperties properties, byte[] bytes) throws Exception
    {
        StoredRun stored = new StoredRun(); stored.ownerId = 7; stored.run = new TestRun();
        stored.run.id = UUID.randomUUID().toString(); stored.run.definitionHash = "a".repeat(64);
        var stage = artifacts.begin(stored); artifacts.capture(stage, "synthetic.csv", "text/plain", bytes.length, out -> out.write(bytes));
        stored.run.status = "SUCCEEDED"; stored.run.cleanupConfirmed = true; artifacts.publish(stage, stored); var repository = new DataGovernanceFileRunRepository(properties); try { repository.save(stored); } finally { repository.close(); } return stored;
    }
    static ProfileInput input(String mode)
    { return new ProfileInput(null, "loopback-fixture", "127.0.0.1", 2121, "test-user", "synthetic-password", "/", mode, "UTF-8"); }
    static Job terminal(DataGovernanceFtpDelivery service, String id) throws Exception
    {
        long deadline = System.nanoTime() + 5_000_000_000L; Job job;
        do { job = service.job(id, 7); if (!Set.of("QUEUED", "RUNNING").contains(job.status())) return job; Thread.sleep(10); } while (System.nanoTime() < deadline);
        throw new AssertionError("delivery did not terminate");
    }
    static class Fake extends DataGovernanceFtpDelivery
    {
        final Map<String, byte[]> remote = new ConcurrentHashMap<>();
        final AtomicBoolean failRename = new AtomicBoolean();
        final AtomicBoolean failAfterMarker = new AtomicBoolean();
        int uploads;
        final DataGovernanceFileRunRepository repository;
        Fake(DataGovernanceProperties p, DataGovernanceArtifactStore a) throws Exception { this(p, a, new DataGovernanceFileRunRepository(p)); }
        Fake(DataGovernanceProperties p, DataGovernanceArtifactStore a, DataGovernanceFileRunRepository repository) throws Exception {
            super(p, DataGovernanceConnectionsTest.crypto(), a, repository); this.repository = repository;
        }
        @Override public void close() { super.close(); repository.close(); }
        @Override Remote connect(Profile p, String password, long deadline)
        {
            return new Remote() {
                String folder;
                public void directory(String path) { }
                public void batchDirectory(String name) { folder = name + "/"; }
                public boolean exists(String name) { return remote.containsKey(folder + name); }
                public void upload(String name, InputStream input, String mode) throws Exception { remote.put(folder + name, input.readAllBytes()); uploads++; }
                public void verify(String name, long bytes, String sha) throws Exception {
                    byte[] content = remote.get(folder + name);
                    if (content == null || content.length != bytes || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)).equals(sha)) throw new IOException("mismatch");
                }
                public void rename(String from, String to) throws Exception {
                    if (!to.equals("_SUCCESS.json") && failRename.compareAndSet(true, false)) throw new IOException("synthetic disconnect before rename");
                    remote.put(folder + to, remote.remove(folder + from));
                    if (to.equals("_SUCCESS.json") && failAfterMarker.compareAndSet(true, false)) throw new IOException("reply lost after marker rename");
                }
                public void close() { }
            };
        }
    }
    @Test void completePayloadIsVerifiedAndCompletionMarkerIsLastAndIdempotent() throws Exception
    {
        var artifacts = new DataGovernanceArtifactStore(properties());
        byte[] bytes = ("中文|\u001fvalue\n".repeat(1200)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StoredRun run = published(artifacts, properties(), bytes); Fake service = new Fake(properties(), artifacts);
        try
        {
            Profile profile = service.saveProfile(null, input("BINARY"), 7);
            Job job = terminal(service, service.submit(new Submit(run.run.id, profile.id()), 7).id());
            assertEquals("DELIVERED", job.status()); assertTrue(job.entries().stream().allMatch(e -> e.status().equals("VERIFIED")));
            assertTrue(service.remote.keySet().stream().anyMatch(k -> k.endsWith("/_SUCCESS.json")));
            assertArrayEquals(bytes, service.remote.values().stream().filter(v -> v.length == bytes.length).findFirst().orElseThrow());
            assertArrayEquals(bytes, artifacts.read(run.run.id, job.entries().get(0).artifactId(), 7).readAllBytes());
            int uploads = service.uploads;
            assertEquals(job.id(), service.submit(new Submit(run.run.id, profile.id()), 7).id()); assertEquals(uploads, service.uploads);
            assertEquals(1, service.jobs(null, 7).size());
            assertThrows(ServiceException.class, () -> service.submit(new Submit(run.run.id, profile.id()), 8));
            assertThrows(ServiceException.class, () -> service.job(job.id(), 8));
            assertFalse(Files.readString(temporary.resolve("deliveries/profile-" + profile.id() + ".json")).contains("synthetic-password"));
        }
        finally { service.close(); }
    }
    @Test void failedRenameCannotPublishBatchAndExplicitRetryVerifiesExistingBytes() throws Exception
    {
        var artifacts = new DataGovernanceArtifactStore(properties()); StoredRun run = published(artifacts, properties(), "synthetic\n".getBytes());
        Fake service = new Fake(properties(), artifacts);
        try
        {
            Profile profile = service.saveProfile(null, input("BINARY"), 7); service.failRename.set(true);
            Job failed = terminal(service, service.submit(new Submit(run.run.id, profile.id()), 7).id());
            assertEquals("FAILED", failed.status()); assertFalse(service.remote.keySet().stream().anyMatch(k -> k.endsWith("/_SUCCESS.json")));
            assertThrows(ServiceException.class, () -> service.retry(failed.id(), new Retry(failed.revision() - 1), 7));
            Job done = terminal(service, service.retry(failed.id(), new Retry(failed.revision()), 7).id());
            assertEquals("DELIVERED", done.status()); assertEquals(2, done.attempts());
            assertThrows(ServiceException.class, () -> service.retry(done.id(), new Retry(done.revision()), 7));
        }
        finally { service.close(); }
    }
    @Test void unknownRemoteFinalContentIsNeverOverwrittenOrMarkedSuccessful() throws Exception
    {
        var artifacts = new DataGovernanceArtifactStore(properties()); StoredRun run = published(artifacts, properties(), "expected".getBytes());
        Fake service = new Fake(properties(), artifacts);
        try
        {
            Profile profile = service.saveProfile(null, input("BINARY"), 7); service.failRename.set(true);
            Job failed = terminal(service, service.submit(new Submit(run.run.id, profile.id()), 7).id());
            String prefix = failed.remoteDirectory().substring(failed.remoteDirectory().lastIndexOf('/') + 1) + "/";
            service.remote.put(prefix + failed.entries().get(0).filename(), "conflict".getBytes());
            Job again = terminal(service, service.retry(failed.id(), new Retry(failed.revision()), 7).id());
            assertEquals("FAILED", again.status()); assertArrayEquals("conflict".getBytes(), service.remote.get(prefix + failed.entries().get(0).filename()));
            assertFalse(service.remote.containsKey(prefix + "_SUCCESS.json"));
        }
        finally { service.close(); }
    }
    @Test void rejectsUnknownArtifactsBadEndpointsPathsAndStaleProfiles() throws Exception
    {
        Fake service = new Fake(properties(), new DataGovernanceArtifactStore(properties()));
        try
        {
            Profile profile = service.saveProfile(null, input("BINARY"), 7);
            assertThrows(ServiceException.class, () -> service.submit(new Submit(UUID.randomUUID().toString(), profile.id()), 7));
            assertThrows(ServiceException.class, () -> service.endpoint("127.0.0.1", 21));
            assertThrows(ServiceException.class, () -> service.endpoint("example.org", 2121));
            assertThrows(ServiceException.class, () -> service.endpoint("127.0.0.1\r\nUSER evil", 2121));
            assertThrows(ServiceException.class, () -> service.saveProfile(profile.id(), input("BINARY"), 7));
            for (String path : List.of("relative", "/../x", "/x/./y", "/x\r\nDELE y", "/x//y")) assertThrows(ServiceException.class, () -> directory(path));
            assertEquals("/safe/path", directory("/safe/path"));
        }
        finally { service.close(); }
    }
    @Test void interruptedPersistedDeliveryRequiresExplicitRecoveryAndKeepsSource() throws Exception
    {
        var artifacts = new DataGovernanceArtifactStore(properties()); StoredRun run = published(artifacts, properties(), "persisted".getBytes());
        String id = UUID.randomUUID().toString(), profileId = UUID.randomUUID().toString();
        Profile profile = new Profile(profileId, 1, "fixture", "127.0.0.1", 2121, "user", "/", "BINARY", "UTF-8", true, "2026-01-01T00:00:00Z");
        StoredJob stored = new StoredJob(); stored.owner = 7; stored.target = profile; stored.encryptedPassword = DataGovernanceConnectionsTest.crypto().encrypt("fixture");
        stored.job = new Job(id, 2, run.run.id, profileId, "fixture", 1, 1, "RUNNING", "/batch-fixture", "BINARY", List.of(), null, 1, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
        try (var files = new DataGovernanceDeliveryFiles(properties())) { files.write("job", id, stored); }
        Fake service = new Fake(properties(), artifacts);
        try { assertEquals("RECOVERY_REQUIRED", service.job(id, 7).status()); assertEquals(0, service.uploads); assertNotNull(artifacts.manifest(run.run.id, 7)); }
        finally { service.close(); }
    }
    @Test void storageRejectsSymbolicLinksAndConcurrentInstances() throws Exception
    {
        try (var files = new DataGovernanceDeliveryFiles(properties()))
        {
            assertThrows(ServiceException.class, () -> new DataGovernanceDeliveryFiles(properties()));
            Path outside = temporary.resolve("outside.json"); Files.writeString(outside, "{}"); String id = UUID.randomUUID().toString();
            Files.createSymbolicLink(temporary.resolve("deliveries/profile-" + id + ".json"), outside);
            assertThrows(ServiceException.class, () -> files.read("profile", id, StoredProfile.class));
        }
    }
    @Test void publishedManifestCannotBypassAnUnconfirmedOriginalRun() throws Exception
    {
        var artifacts = new DataGovernanceArtifactStore(properties()); StoredRun run = published(artifacts, properties(), "guarded".getBytes());
        run.run.status = "CLEANUP_REQUIRED"; run.run.cleanupConfirmed = false; var repository = new DataGovernanceFileRunRepository(properties()); try { repository.save(run); } finally { repository.close(); }
        Fake service = new Fake(properties(), artifacts);
        try
        {
            Profile profile = service.saveProfile(null, input("BINARY"), 7);
            assertNotNull(artifacts.manifest(run.run.id, 7));
            assertThrows(ServiceException.class, () -> service.submit(new Submit(run.run.id, profile.id()), 7));
            assertEquals(0, service.uploads); assertTrue(service.jobs(null, 7).isEmpty());
        }
        finally { service.close(); }
    }
    @Test void retryAfterMarkerReplyLossOnlyVerifiesAndNeverRefillsConsumedFiles() throws Exception
    {
        var artifacts = new DataGovernanceArtifactStore(properties()); StoredRun run = published(artifacts, properties(), "guarded".getBytes());
        Fake service = new Fake(properties(), artifacts);
        try
        {
            Profile profile = service.saveProfile(null, input("BINARY"), 7); service.failAfterMarker.set(true);
            Job failed = terminal(service, service.submit(new Submit(run.run.id, profile.id()), 7).id());
            assertEquals("FAILED", failed.status()); assertTrue(service.remote.keySet().stream().anyMatch(k -> k.endsWith("/_SUCCESS.json")));
            int count = service.uploads;
            String folder = failed.remoteDirectory().substring(failed.remoteDirectory().lastIndexOf('/') + 1) + "/";
            byte[] content = service.remote.remove(folder + failed.entries().get(0).filename());
            Job unresolved = terminal(service, service.retry(failed.id(), new Retry(failed.revision()), 7).id());
            assertEquals("RECOVERY_REQUIRED", unresolved.status()); assertEquals(count, service.uploads);
            assertFalse(service.remote.containsKey(folder + failed.entries().get(0).filename()));
            service.remote.put(folder + failed.entries().get(0).filename(), content);
            Job verified = terminal(service, service.retry(unresolved.id(), new Retry(unresolved.revision()), 7).id());
            assertEquals("DELIVERED", verified.status()); assertEquals(count, service.uploads);
        }
        finally { service.close(); }
    }
    @Test void explicitCredentialRefreshPreservesBatchIdentityAndRejectsTargetDrift() throws Exception
    {
        var artifacts = new DataGovernanceArtifactStore(properties()); StoredRun run = published(artifacts, properties(), "credentials".getBytes());
        var other = published(artifacts, properties(), "other".getBytes());
        Fake service = new Fake(properties(), artifacts);
        try
        {
            Profile profile = service.saveProfile(null, input("BINARY"), 7); service.failRename.set(true);
            Job failed = terminal(service, service.submit(new Submit(run.run.id, profile.id()), 7).id());
            Profile updated = service.saveProfile(profile.id(), new ProfileInput(profile.revision(), "fixture", "127.0.0.1", 2121, "new-user", "new-password", "/", "BINARY", "UTF-8"), 7);
            assertEquals(failed.id(), service.submit(new Submit(run.run.id, profile.id()), 7).id());
            Job done = terminal(service, service.retry(failed.id(), new Retry(failed.revision(), true), 7).id());
            assertEquals("DELIVERED", done.status()); assertEquals(updated.revision(), done.credentialRevision()); assertEquals(profile.revision(), done.connectionRevision());
            service.failRename.set(true);
            Job failedAgain = terminal(service, service.submit(new Submit(other.run.id, profile.id()), 7).id());
            service.saveProfile(profile.id(), new ProfileInput(updated.revision(), "fixture", "127.0.0.1", 2121, "new-user", "", "/elsewhere", "BINARY", "UTF-8"), 7);
            assertThrows(ServiceException.class, () -> service.retry(failedAgain.id(), new Retry(failedAgain.revision(), true), 7));
        }
        finally { service.close(); }
    }
    @Test void metadataListingHasByteEntryAndAbsoluteTimeLimits() throws Exception
    {
        String row = "type=file;size=4; example.csv\r\n";
        long deadline = System.nanoTime() + 5_000_000_000L;
        assertEquals("example.csv", BoundedFtpClient.readListing(new ByteArrayInputStream(row.getBytes()), "UTF-8", deadline).get(0).getName());
        assertThrows(IOException.class, () -> BoundedFtpClient.readListing(new ByteArrayInputStream(row.repeat(211).getBytes()), "UTF-8", deadline));
        assertThrows(IOException.class, () -> BoundedFtpClient.readListing(new ByteArrayInputStream("x".repeat(70000).getBytes()), "UTF-8", deadline));
        assertThrows(IOException.class, () -> BoundedFtpClient.readListing(new ByteArrayInputStream(row.getBytes()), "UTF-8", System.nanoTime() - 1));
    }
    @Test void scheduledTargetBindingAllowsCredentialRotationButRejectsDestinationDriftAtomically() throws Exception
    {
        var properties = properties(); var artifacts = new DataGovernanceArtifactStore(properties);
        var run = published(artifacts, properties, "scheduled".getBytes()); Fake service = new Fake(properties, artifacts);
        try
        {
            Profile profile = service.saveProfile(null, input("BINARY"), 7);
            var adapter = new DataGovernanceScheduleDeliveryAdapter(service); var binding = adapter.target(profile.id(), 7);
            Profile rotated = service.saveProfile(profile.id(), new ProfileInput(profile.revision(), "new name", "127.0.0.1", 2121, profile.username(), "rotated", "/", "BINARY", "UTF-8"), 7);
            assertEquals(binding.fingerprint(), adapter.target(profile.id(), 7).fingerprint());
            service.saveProfile(profile.id(), new ProfileInput(rotated.revision(), "new name", "127.0.0.1", 2121, profile.username(), "", "/other", "BINARY", "UTF-8"), 7);
            assertThrows(ServiceException.class, () -> adapter.submit(run.run.id, binding, 7));
            assertEquals(0, service.uploads); assertNull(adapter.find(run.run.id, profile.id(), 7));
            properties.setFtpAllowedEndpoints(List.of()); assertThrows(ServiceException.class, () -> adapter.target(profile.id(), 7));
        }
        finally { service.close(); }
    }
}
