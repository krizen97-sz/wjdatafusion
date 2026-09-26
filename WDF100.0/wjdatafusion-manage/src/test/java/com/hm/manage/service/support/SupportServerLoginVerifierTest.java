package com.hm.manage.service.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

class SupportServerLoginVerifierTest
{
    @Test void requiresExplicitMatchingFingerprint()
    {
        var first = new SupportServerLoginVerifier.FingerprintRepository(null);
        assertEquals(HostKeyRepository.NOT_INCLUDED, first.check("host", new byte[] {1,2,3}));
        assertTrue(first.fingerprint.startsWith("SHA256:"));
        var confirmed = new SupportServerLoginVerifier.FingerprintRepository(first.fingerprint);
        assertEquals(HostKeyRepository.OK, confirmed.check("host", new byte[] {1,2,3}));
        assertEquals(HostKeyRepository.NOT_INCLUDED, confirmed.check("host", new byte[] {4,5,6}));
    }
    @Test void directRootMustBeUidZeroAndClosesSession() throws Exception
    {
        Session session = mock(Session.class);
        var verifier = stub(session);
        doReturn(new SupportServerLoginVerifier.Probe(0, false)).when(verifier).execute(eq(session), anyString(), anyString(), isNull());
        var result = verifier.verify("host", 22, "root", "secret", "secret", true, null);
        assertEquals("SUCCESS", result.status()); assertTrue(result.rootPasswordVerified());
        assertFalse(result.toString().contains("secret"));
        verify(session).disconnect();
    }
    @Test void suRequiresChallengeAndRootIdentity() throws Exception
    {
        Session session = mock(Session.class);
        var verifier = stub(session);
        doReturn(new SupportServerLoginVerifier.Probe(1000, false)).when(verifier).execute(eq(session), anyString(), anyString(), isNull());
        doReturn(new SupportServerLoginVerifier.Probe(0, true)).when(verifier).execute(eq(session), anyString(), anyString(), eq("root-secret"));
        var result = verifier.verify("host", 55555, "hik", "hik-secret", "root-secret", false, null);
        assertEquals("SUCCESS", result.status()); assertEquals(3, result.steps().size());
        assertTrue(result.rootPasswordVerified()); verify(session).disconnect();
    }
    @Test void passwordlessSuIsNotPasswordVerification() throws Exception
    {
        Session session = mock(Session.class);
        var verifier = stub(session);
        doReturn(new SupportServerLoginVerifier.Probe(1000, false)).when(verifier).execute(eq(session), anyString(), anyString(), isNull());
        doReturn(new SupportServerLoginVerifier.Probe(0, false)).when(verifier).execute(eq(session), anyString(), anyString(), anyString());
        var result = verifier.verify("host", 22, "hik", "h", "r", false, null);
        assertEquals("PASSWORD_NOT_VERIFIED", result.status()); assertFalse(result.rootPasswordVerified());
    }
    @Test void rejectedSshNeverRunsSuOrEchoesException() throws Exception
    {
        Session session = mock(Session.class);
        var verifier = stub(session);
        doThrow(new JSchException("Auth fail with secret-content")).when(session).connect(anyInt());
        var result = verifier.verify("host", 22, "hik", "h", "r", false, null);
        assertEquals("AUTH_REJECTED", result.status()); assertFalse(result.toString().contains("secret-content"));
        verify(verifier, never()).execute(any(), anyString(), anyString(), any());
        verify(session).disconnect();
    }
    @Test void sendsSecretOnlyAfterPromptAndNotInCommand() throws Exception
    {
        Session session = mock(Session.class); ChannelExec channel = mock(ChannelExec.class);
        when(session.openChannel("exec")).thenReturn(channel);
        AtomicBoolean sent = new AtomicBoolean();
        ByteArrayInputStream before = new ByteArrayInputStream("Password: ".getBytes());
        ByteArrayInputStream after = new ByteArrayInputStream("\r\n__TEST_0__\r\n".getBytes());
        var input = new java.io.InputStream() {
            public int available() { return (sent.get() ? after : before).available(); }
            public int read() { return (sent.get() ? after : before).read(); }
            public int read(byte[] b, int off, int len) { return (sent.get() ? after : before).read(b, off, len); }
        };
        ByteArrayOutputStream submitted = new ByteArrayOutputStream() {
            public void flush() { assertEquals(0, before.available()); sent.set(true); }
        };
        when(channel.getInputStream()).thenReturn(input);
        when(channel.getErrStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(channel.getOutputStream()).thenReturn(submitted);
        when(channel.isClosed()).thenAnswer(call -> sent.get() && after.available() == 0);
        when(channel.getExitStatus()).thenReturn(0);
        var result = new SupportServerLoginVerifier().execute(session, "fixed-su-command", "__TEST_", "test-secret");
        assertEquals(0, result.uid()); assertTrue(result.passwordSent());
        assertEquals("test-secret\n", submitted.toString());
        verify(channel).setCommand("fixed-su-command"); verify(channel).setPty(true); verify(channel).disconnect();
    }
    @Test void excessiveOutputIsBoundedAndDisconnected() throws Exception
    {
        Session session = mock(Session.class); ChannelExec channel = mock(ChannelExec.class);
        when(session.openChannel("exec")).thenReturn(channel);
        when(channel.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[20000]));
        when(channel.getErrStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(channel.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        assertThrows(Exception.class, () -> new SupportServerLoginVerifier().execute(session, "id -u", "mark", null));
        verify(channel).disconnect();
    }
    private SupportServerLoginVerifier stub(Session session) throws Exception
    {
        var userInfo = new SupportServerLoginVerifier.PasswordUserInfo("test-secret");
        userInfo.promptPassword("test");
        when(session.getUserInfo()).thenReturn(userInfo);
        var verifier = spy(new SupportServerLoginVerifier());
        doReturn(session).when(verifier).createSession(anyString(), anyInt(), anyString(), anyString(), any());
        return verifier;
    }

    @Test void noPasswordAuthenticationCannotReportPasswordCorrect() throws Exception
    {
        Session session = mock(Session.class);
        var verifier = stub(session);
        when(session.getUserInfo()).thenReturn(new SupportServerLoginVerifier.PasswordUserInfo("unused"));
        var result = verifier.verify("host", 22, "root", "unused", "unused", true, null);
        assertEquals("PASSWORD_NOT_VERIFIED", result.status());
        assertFalse(result.rootPasswordVerified());
        verify(verifier, never()).execute(any(), anyString(), anyString(), any());
    }
}
