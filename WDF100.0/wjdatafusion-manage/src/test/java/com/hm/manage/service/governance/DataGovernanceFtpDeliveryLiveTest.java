package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.manage.service.governance.DataGovernanceFtpDelivery.*;
import org.apache.commons.net.ftp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="GOVERNANCE_FTP_CREDENTIALS", matches=".+")
class DataGovernanceFtpDeliveryLiveTest
{
    @TempDir Path temporary;
    @Test void binaryAndAsciiOnOwnedLoopbackFtpKeepFullBytesAndPublishBatchMarker() throws Exception
    {
        Path path = Path.of(System.getenv("GOVERNANCE_FTP_CREDENTIALS"));
        var mapper = new ObjectMapper(); var credentials = mapper.readTree(Files.readAllBytes(path));
        var runtime = mapper.readTree(Files.readAllBytes(path.getParent().getParent().resolve("runtime.json")));
        assertEquals("rynew-data-governance-runtime", runtime.path("owner").asText());
        assertEquals(path.getParent().getParent().resolve("ftp/files").toRealPath(), Path.of(credentials.path("root").asText()).toRealPath());
        var properties = new DataGovernanceProperties(); properties.setStorageDir(temporary.toString()); properties.getNifi().setBaseUrl("https://localhost:9443/nifi-api");
        var artifacts = new DataGovernanceArtifactStore(properties);
        byte[] payload = ("message|\u001fpicture\n" + "合成数据|\u001f\n".repeat(2400)).getBytes(StandardCharsets.UTF_8);
        assertTrue(payload.length > 8192);
        var run = DataGovernanceFtpDeliveryTest.published(artifacts, properties, payload);
        var repository = new DataGovernanceFileRunRepository(properties);
        var service = new DataGovernanceFtpDelivery(properties, DataGovernanceConnectionsTest.crypto(), artifacts, repository);
        List<Job> created = new ArrayList<>();
        try
        {
            for (String mode : List.of("BINARY", "ASCII"))
            {
                Profile profile = service.saveProfile(null, new ProfileInput(null, "synthetic-" + mode, "127.0.0.1", 2121,
                    credentials.path("username").asText(), credentials.path("password").asText(), "/", mode, mode.equals("ASCII") ? "ISO-8859-1" : "UTF-8"), 7);
                assertEquals(true, service.test(profile.id(), 7).get("success"));
                Job job = service.submit(new Submit(run.run.id, profile.id()), 7); created.add(job);
                long deadline = System.nanoTime() + 30_000_000_000L;
                while (Set.of("QUEUED", "RUNNING").contains(job.status()) && System.nanoTime() < deadline)
                { Thread.sleep(50); job = service.job(job.id(), 7); }
                assertEquals("DELIVERED", job.status());
                FTPClient ftp = new FTPClient();
                try
                {
                    ftp.setConnectTimeout(3000); ftp.connect("127.0.0.1", 2121); assertTrue(ftp.login(credentials.path("username").asText(), credentials.path("password").asText()));
                    ftp.enterLocalPassiveMode(); assertTrue(ftp.setFileType(FTP.BINARY_FILE_TYPE)); assertTrue(ftp.changeWorkingDirectory(job.remoteDirectory()));
                    var received = new ByteArrayOutputStream(); assertTrue(ftp.retrieveFile(job.entries().get(0).filename(), received)); assertArrayEquals(payload, received.toByteArray());
                    var marker = new ByteArrayOutputStream(); assertTrue(ftp.retrieveFile("_SUCCESS.json", marker));
                    assertEquals(run.run.id, mapper.readTree(marker.toByteArray()).path("runId").asText());
                    assertEquals(1, mapper.readTree(marker.toByteArray()).path("files").size());
                    assertTrue(Arrays.stream(ftp.listFiles()).noneMatch(file -> file.getName().endsWith(".temp")));
                }
                finally { if (ftp.isConnected()) ftp.disconnect(); }
            }
        }
        finally
        {
            service.close(); repository.close();
            for (Job job : created)
            {
                String expected = "/batch-" + run.run.id + "-" + job.id().substring(0, 8); assertEquals(expected, job.remoteDirectory());
                FTPClient ftp = new FTPClient();
                try
                {
                    ftp.setConnectTimeout(3000); ftp.connect("127.0.0.1", 2121); assertTrue(ftp.login(credentials.path("username").asText(), credentials.path("password").asText())); ftp.enterLocalPassiveMode();
                    if (ftp.changeWorkingDirectory(expected))
                    { for (FTPFile file : ftp.listFiles()) { assertTrue(file.isFile()); assertTrue(ftp.deleteFile(file.getName())); } assertTrue(ftp.changeWorkingDirectory("/")); assertTrue(ftp.removeDirectory(expected)); }
                }
                finally { if (ftp.isConnected()) ftp.disconnect(); }
            }
        }
    }
}
