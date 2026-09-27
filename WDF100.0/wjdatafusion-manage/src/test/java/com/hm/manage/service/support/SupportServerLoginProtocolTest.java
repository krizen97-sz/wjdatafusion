package com.hm.manage.service.support;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SupportServerLoginProtocolTest
{
    @Test void realSshHandshakePasswordAndSuExchange() throws Exception
    {
        assertTimeoutPreemptively(Duration.ofSeconds(35), () -> withFixture("normal", port -> {
            var verifier = new SupportServerLoginVerifier();
            var first = verifier.verify("127.0.0.1", port, "HIK", "hik-test", "root-test", false, null);
            assertEquals("HOST_KEY_REQUIRED", first.status());
            String fingerprint = first.hostFingerprint();
            var twoStage = verifier.verify("127.0.0.1", port, "Hik", "hik-test", "root-test", false, fingerprint);
            assertEquals("SUCCESS", twoStage.status(), twoStage.message());
            assertEquals("hik", twoStage.loginUsername());
            assertTrue(twoStage.rootPasswordVerified());
            var direct = verifier.verify("127.0.0.1", port, "root", "root-test", "root-test", true, fingerprint);
            assertEquals("SUCCESS", direct.status(), direct.message());
            var wrongHik = verifier.verify("127.0.0.1", port, "hik", "wrong", "root-test", false, fingerprint);
            assertEquals("AUTH_REJECTED", wrongHik.status());
            var wrongRoot = verifier.verify("127.0.0.1", port, "hik", "hik-test", "wrong", false, fingerprint);
            assertEquals("SU_REJECTED", wrongRoot.status());
            var wrongDirect = verifier.verify("127.0.0.1", port, "root", "wrong", "wrong", true, fingerprint);
            assertEquals("AUTH_REJECTED", wrongDirect.status());
            var changed = verifier.verify("127.0.0.1", port, "root", "root-test", "root-test", true, "SHA256:" + "A".repeat(43));
            assertEquals("HOST_KEY_CHANGED", changed.status());
        }));
    }
    @Test void realPasswordlessSuCannotVerifyPassword() throws Exception
    {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> withFixture("passwordless", port -> {
            var verifier = new SupportServerLoginVerifier();
            var first = verifier.verify("127.0.0.1", port, "hik", "hik-test", "wrong", false, null);
            var result = verifier.verify("127.0.0.1", port, "hik", "hik-test", "wrong", false, first.hostFingerprint());
            assertEquals("PASSWORD_NOT_VERIFIED", result.status()); assertFalse(result.rootPasswordVerified());
        }));
    }
    private void withFixture(String mode, java.util.function.IntConsumer test) throws Exception
    {
        String python = System.getenv("RYNEW_SSH_TEST_PYTHON");
        Assumptions.assumeTrue(python != null, "Set RYNEW_SSH_TEST_PYTHON to an isolated Python with paramiko");
        String fixture = Path.of(getClass().getResource("/SupportServerLoginFixture.py").toURI()).toString();
        Process process = new ProcessBuilder(python, fixture, mode).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (var output = new BufferedReader(new InputStreamReader(process.getInputStream())))
        {
            int port = Integer.parseInt(output.readLine());
            test.accept(port);
        }
        finally { process.destroyForcibly(); process.waitFor(); }
    }
}
